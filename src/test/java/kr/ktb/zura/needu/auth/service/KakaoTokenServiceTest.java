package kr.ktb.zura.needu.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import kr.ktb.zura.needu.auth.config.KakaoOAuth2ClientConfig;
import kr.ktb.zura.needu.auth.client.KakaoOAuthClient;
import kr.ktb.zura.needu.auth.entity.KakaoToken;
import kr.ktb.zura.needu.auth.repository.KakaoTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;

class KakaoTokenServiceTest {

    private KakaoTokenRepository kakaoTokenRepository;
    private KakaoTokenCipher kakaoTokenCipher;
    private ClientRegistrationRepository clientRegistrationRepository;
    private KakaoTokenService kakaoTokenService;

    @BeforeEach
    void setUp() {
        kakaoTokenRepository = mock(KakaoTokenRepository.class);
        kakaoTokenCipher = mock(KakaoTokenCipher.class);
        clientRegistrationRepository = mock(ClientRegistrationRepository.class);
        kakaoTokenService = new KakaoTokenService(
                kakaoTokenRepository, kakaoTokenCipher, clientRegistrationRepository, Duration.ofDays(60));
        when(kakaoTokenCipher.encrypt("access-token")).thenReturn("encrypted-access");
        when(kakaoTokenCipher.encrypt("refresh-token")).thenReturn("encrypted-refresh");
    }

    @Test
    void newKakaoTokens_saveTokens_savesEncryptedTokensAndExpirations() {
        when(kakaoTokenRepository.findByUserId(1L)).thenReturn(Optional.empty());
        LocalDateTime before = LocalDateTime.now(ZoneOffset.UTC);

        kakaoTokenService.saveTokens(1L, tokens());

        ArgumentCaptor<KakaoToken> captor = ArgumentCaptor.forClass(KakaoToken.class);
        verify(kakaoTokenRepository).save(captor.capture());
        KakaoToken savedToken = captor.getValue();
        assertEquals(1L, savedToken.getUserId());
        assertEquals("encrypted-access", savedToken.getEncryptedAccessToken());
        assertEquals("encrypted-refresh", savedToken.getEncryptedRefreshToken());
        assertEquals("friends", savedToken.getScopes());
        assertEquals(before.plusHours(6).getHour(), savedToken.getAccessTokenExpiresAt().getHour());
        assertEquals(before.plusDays(60).toLocalDate(), savedToken.getRefreshTokenExpiresAt().toLocalDate());
    }

    @Test
    void existingKakaoToken_saveTokens_updatesSameEntity() {
        KakaoToken existing = new KakaoToken(
                1L,
                "old-access",
                "old-refresh",
                LocalDateTime.now(ZoneOffset.UTC),
                LocalDateTime.now(ZoneOffset.UTC),
                "friends"
        );
        when(kakaoTokenRepository.findByUserId(1L)).thenReturn(Optional.of(existing));

        kakaoTokenService.saveTokens(1L, tokens());

        assertEquals("encrypted-access", existing.getEncryptedAccessToken());
        assertEquals("encrypted-refresh", existing.getEncryptedRefreshToken());
        verify(kakaoTokenRepository).save(existing);
    }

    @Test
    void storedKakaoToken_loadAuthorizedClient_decryptsTokensAndScopes() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        KakaoToken stored = new KakaoToken(
                1L,
                "encrypted-access",
                "encrypted-refresh",
                now.plusHours(6),
                now.plusDays(60),
                "friends");
        ClientRegistration registration = mock(ClientRegistration.class);
        when(kakaoTokenRepository.findByUserId(1L)).thenReturn(Optional.of(stored));
        when(clientRegistrationRepository.findByRegistrationId(KakaoOAuth2ClientConfig.REGISTRATION_ID))
                .thenReturn(registration);
        when(kakaoTokenCipher.decrypt("encrypted-access")).thenReturn("access-token");
        when(kakaoTokenCipher.decrypt("encrypted-refresh")).thenReturn("refresh-token");

        OAuth2AuthorizedClient result = kakaoTokenService.loadAuthorizedClient(
                KakaoOAuth2ClientConfig.REGISTRATION_ID, "1");

        assertEquals("access-token", result.getAccessToken().getTokenValue());
        assertEquals("refresh-token", result.getRefreshToken().getTokenValue());
        assertEquals(Set.of("friends"), result.getAccessToken().getScopes());
    }

    @Test
    void omittedRefreshTokenExpiration_saveAuthorizedClient_preservesStoredExpiration() {
        LocalDateTime storedExpiration = LocalDateTime.now(ZoneOffset.UTC).plusDays(20);
        KakaoToken existing = new KakaoToken(
                1L,
                "old-access",
                "encrypted-refresh",
                LocalDateTime.now(ZoneOffset.UTC),
                storedExpiration,
                "friends");
        when(kakaoTokenRepository.findByUserId(1L)).thenReturn(Optional.of(existing));
        when(kakaoTokenCipher.decrypt("encrypted-refresh")).thenReturn("refresh-token");
        when(kakaoTokenCipher.encrypt("new-access")).thenReturn("encrypted-new-access");
        ClientRegistration registration = mock(ClientRegistration.class);
        Instant now = Instant.now();
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "new-access",
                now,
                now.plusSeconds(21600),
                Set.of("friends"));
        OAuth2RefreshToken refreshToken = new OAuth2RefreshToken("refresh-token", now);
        OAuth2AuthorizedClient authorizedClient = new OAuth2AuthorizedClient(
                registration, "1", accessToken, refreshToken);

        kakaoTokenService.saveAuthorizedClient(
                authorizedClient,
                new UsernamePasswordAuthenticationToken("1", null, Set.of()));

        assertEquals(storedExpiration, existing.getRefreshTokenExpiresAt());
        assertEquals("encrypted-new-access", existing.getEncryptedAccessToken());
        assertEquals("encrypted-refresh", existing.getEncryptedRefreshToken());
    }

    private KakaoOAuthClient.KakaoTokens tokens() {
        return new KakaoOAuthClient.KakaoTokens(
                "access-token",
                "refresh-token",
                Duration.ofHours(6),
                Duration.ofDays(60),
                Set.of("friends")
        );
    }
}
