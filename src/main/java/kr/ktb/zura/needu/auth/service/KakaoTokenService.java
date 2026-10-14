package kr.ktb.zura.needu.auth.service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import kr.ktb.zura.needu.auth.client.KakaoOAuthClient;
import kr.ktb.zura.needu.auth.config.KakaoOAuth2ClientConfig;
import kr.ktb.zura.needu.auth.entity.KakaoToken;
import kr.ktb.zura.needu.auth.repository.KakaoTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KakaoTokenService implements OAuth2AuthorizedClientService {

    private final KakaoTokenRepository kakaoTokenRepository;
    private final KakaoTokenCipher kakaoTokenCipher;
    private final ClientRegistrationRepository clientRegistrationRepository;
    private final Duration refreshTokenExpiration;

    public KakaoTokenService(KakaoTokenRepository kakaoTokenRepository,
                             KakaoTokenCipher kakaoTokenCipher,
                             ClientRegistrationRepository clientRegistrationRepository,
                             @Value("${kakao.oauth.refresh-token-expiration}") Duration refreshTokenExpiration) {
        this.kakaoTokenRepository = kakaoTokenRepository;
        this.kakaoTokenCipher = kakaoTokenCipher;
        this.clientRegistrationRepository = clientRegistrationRepository;
        this.refreshTokenExpiration = refreshTokenExpiration;
    }

    @Transactional
    public void saveTokens(Long userId, KakaoOAuthClient.KakaoTokens tokens) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        save(userId,
                tokens.accessToken(),
                tokens.refreshToken(),
                now.plus(tokens.accessTokenExpiresIn()),
                now.plus(tokens.refreshTokenExpiresIn()),
                tokens.scopes());
    }

    @Override
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(
            String clientRegistrationId, String principalName) {
        if (!KakaoOAuth2ClientConfig.REGISTRATION_ID.equals(clientRegistrationId)) {
            return null;
        }
        Long userId;
        try {
            userId = Long.valueOf(principalName);
        } catch (NumberFormatException exception) {
            return null;
        }
        Optional<KakaoToken> storedToken = kakaoTokenRepository.findByUserId(userId);
        if (storedToken.isEmpty()) {
            return null;
        }
        ClientRegistration registration = clientRegistrationRepository.findByRegistrationId(clientRegistrationId);
        KakaoToken token = storedToken.get();
        // updatedAt은 JVM 기본 시간대로 기록되어 UTC 만료 시각과 비교할 수 없고, 토큰 갱신 판단에는 expiresAt만 쓰인다.
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                kakaoTokenCipher.decrypt(token.getEncryptedAccessToken()),
                null,
                token.getAccessTokenExpiresAt().toInstant(ZoneOffset.UTC),
                parseScopes(token.getScopes())
        );
        OAuth2RefreshToken refreshToken = new OAuth2RefreshToken(
                kakaoTokenCipher.decrypt(token.getEncryptedRefreshToken()),
                null,
                token.getRefreshTokenExpiresAt().toInstant(ZoneOffset.UTC)
        );
        return (T) new OAuth2AuthorizedClient(registration, principalName, accessToken, refreshToken);
    }

    @Override
    @Transactional
    public void saveAuthorizedClient(OAuth2AuthorizedClient authorizedClient, Authentication principal) {
        OAuth2RefreshToken refreshToken = authorizedClient.getRefreshToken();
        if (refreshToken == null || authorizedClient.getAccessToken().getExpiresAt() == null) {
            throw new IllegalArgumentException("Kakao access and refresh tokens must have expiration data");
        }
        Long userId = Long.valueOf(principal.getName());
        Optional<KakaoToken> existing = kakaoTokenRepository.findByUserId(userId);
        LocalDateTime refreshExpiresAt = resolveRefreshExpiresAt(existing, refreshToken);
        save(userId,
                authorizedClient.getAccessToken().getTokenValue(),
                refreshToken.getTokenValue(),
                LocalDateTime.ofInstant(authorizedClient.getAccessToken().getExpiresAt(), ZoneOffset.UTC),
                refreshExpiresAt,
                authorizedClient.getAccessToken().getScopes());
    }

    @Override
    @Transactional
    public void removeAuthorizedClient(String clientRegistrationId, String principalName) {
        if (!KakaoOAuth2ClientConfig.REGISTRATION_ID.equals(clientRegistrationId)) {
            return;
        }
        try {
            kakaoTokenRepository.findByUserId(Long.valueOf(principalName))
                    .ifPresent(kakaoTokenRepository::delete);
        } catch (NumberFormatException ignored) {
            // OAuth2 principal names outside NEEDU user IDs do not belong to this store.
        }
    }

    private void save(Long userId,
                      String accessToken,
                      String refreshToken,
                      LocalDateTime accessExpiresAt,
                      LocalDateTime refreshExpiresAt,
                      Set<String> scopes) {
        String encryptedAccessToken = kakaoTokenCipher.encrypt(accessToken);
        String encryptedRefreshToken = kakaoTokenCipher.encrypt(refreshToken);
        String storedScopes = scopes.stream().sorted().collect(Collectors.joining(","));
        KakaoToken kakaoToken = kakaoTokenRepository.findByUserId(userId)
                .orElseGet(() -> new KakaoToken(
                        userId,
                        encryptedAccessToken,
                        encryptedRefreshToken,
                        accessExpiresAt,
                        refreshExpiresAt,
                        storedScopes
                ));
        kakaoToken.update(
                encryptedAccessToken,
                encryptedRefreshToken,
                accessExpiresAt,
                refreshExpiresAt,
                storedScopes
        );
        kakaoTokenRepository.save(kakaoToken);
    }

    private LocalDateTime resolveRefreshExpiresAt(Optional<KakaoToken> existing, OAuth2RefreshToken refreshToken) {
        if (refreshToken.getExpiresAt() != null) {
            return LocalDateTime.ofInstant(refreshToken.getExpiresAt(), ZoneOffset.UTC);
        }
        if (existing.isPresent()
                && kakaoTokenCipher.decrypt(existing.get().getEncryptedRefreshToken())
                .equals(refreshToken.getTokenValue())) {
            return existing.get().getRefreshTokenExpiresAt();
        }
        return LocalDateTime.now(ZoneOffset.UTC).plus(refreshTokenExpiration);
    }

    private Set<String> parseScopes(String scopes) {
        if (scopes == null || scopes.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(scopes.split(","))
                .filter(scope -> !scope.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }
}
