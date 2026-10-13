package kr.ktb.zura.needu.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import kr.ktb.zura.needu.auth.client.KakaoOAuthClient;
import kr.ktb.zura.needu.auth.entity.KakaoToken;
import kr.ktb.zura.needu.auth.repository.KakaoTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class KakaoTokenServiceTest {

    private KakaoTokenRepository kakaoTokenRepository;
    private KakaoTokenCipher kakaoTokenCipher;
    private KakaoTokenService kakaoTokenService;

    @BeforeEach
    void setUp() {
        kakaoTokenRepository = mock(KakaoTokenRepository.class);
        kakaoTokenCipher = mock(KakaoTokenCipher.class);
        kakaoTokenService = new KakaoTokenService(kakaoTokenRepository, kakaoTokenCipher);
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
                LocalDateTime.now(ZoneOffset.UTC)
        );
        when(kakaoTokenRepository.findByUserId(1L)).thenReturn(Optional.of(existing));

        kakaoTokenService.saveTokens(1L, tokens());

        assertEquals("encrypted-access", existing.getEncryptedAccessToken());
        assertEquals("encrypted-refresh", existing.getEncryptedRefreshToken());
        verify(kakaoTokenRepository).save(existing);
    }

    private KakaoOAuthClient.KakaoTokens tokens() {
        return new KakaoOAuthClient.KakaoTokens(
                "access-token",
                "refresh-token",
                Duration.ofHours(6),
                Duration.ofDays(60)
        );
    }
}
