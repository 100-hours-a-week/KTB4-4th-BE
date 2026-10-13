package kr.ktb.zura.needu.auth.service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import kr.ktb.zura.needu.auth.client.KakaoOAuthClient;
import kr.ktb.zura.needu.auth.entity.KakaoToken;
import kr.ktb.zura.needu.auth.repository.KakaoTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class KakaoTokenService {

    private final KakaoTokenRepository kakaoTokenRepository;
    private final KakaoTokenCipher kakaoTokenCipher;

    @Transactional
    public void saveTokens(Long userId, KakaoOAuthClient.KakaoTokens tokens) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        String encryptedAccessToken = kakaoTokenCipher.encrypt(tokens.accessToken());
        String encryptedRefreshToken = kakaoTokenCipher.encrypt(tokens.refreshToken());
        KakaoToken kakaoToken = kakaoTokenRepository.findByUserId(userId)
                .orElseGet(() -> new KakaoToken(
                        userId,
                        encryptedAccessToken,
                        encryptedRefreshToken,
                        now.plus(tokens.accessTokenExpiresIn()),
                        now.plus(tokens.refreshTokenExpiresIn())
                ));
        kakaoToken.update(
                encryptedAccessToken,
                encryptedRefreshToken,
                now.plus(tokens.accessTokenExpiresIn()),
                now.plus(tokens.refreshTokenExpiresIn())
        );
        kakaoTokenRepository.save(kakaoToken);
    }
}
