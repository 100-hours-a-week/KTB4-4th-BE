package kr.ktb.zura.needu.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import kr.ktb.zura.needu.auth.entity.AuthSession;
import kr.ktb.zura.needu.auth.exception.AuthErrorCode;
import kr.ktb.zura.needu.auth.repository.AuthSessionRepository;
import kr.ktb.zura.needu.auth.repository.RefreshTokenReuseGraceRepository;
import kr.ktb.zura.needu.auth.type.RevokeReason;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.test.util.ReflectionTestUtils;

class AuthSessionServiceTest {

    @Test
    void existingSessions_deleteAllByUserId_deletesEverySession() {
        AuthSessionRepository repository = mock(AuthSessionRepository.class);
        AuthSessionService service = createService(repository, mock(RefreshTokenReuseGraceRepository.class));

        service.deleteAllByUserId(1L);

        verify(repository).deleteAllByUserId(1L);
    }

    @Test
    void activeSessions_revokeAllByUserId_revokesEverySession() {
        AuthSessionRepository repository = mock(AuthSessionRepository.class);
        AuthSessionService service = createService(repository, mock(RefreshTokenReuseGraceRepository.class));
        AuthSession first = new AuthSession(1L, new byte[]{1}, LocalDateTime.now().plusDays(14));
        AuthSession second = new AuthSession(1L, new byte[]{2}, LocalDateTime.now().plusDays(14));
        when(repository.findAllByUserIdAndRevokedAtIsNull(1L)).thenReturn(List.of(first, second));

        service.revokeAllByUserId(1L, RevokeReason.BLOCKED);

        assertThat(List.of(first, second)).allSatisfy(session -> {
            assertThat(session.isRevoked()).isTrue();
            assertThat(session.getRevokeReason()).isEqualTo(RevokeReason.BLOCKED);
        });
    }

    private static final String TOKEN = "a".repeat(43);
    private static final Duration REUSE_GRACE = Duration.ofSeconds(10);

    @Test
    void currentToken_rotate_issuesNewTokenAndSavesReuseGrace() throws Exception {
        AuthSessionRepository repository = mock(AuthSessionRepository.class);
        RefreshTokenReuseGraceRepository graceRepository = mock(RefreshTokenReuseGraceRepository.class);
        AuthSessionService service = createService(repository, graceRepository);
        AuthSession session = session(7L);
        when(repository.findByRefreshTokenHash(any())).thenReturn(Optional.of(session));

        AuthSessionService.RefreshToken refreshToken = service.rotate(TOKEN);

        assertThat(refreshToken.value()).isNotNull().isNotEqualTo(TOKEN);
        assertThat(session.getRefreshTokenHash()).isEqualTo(sha256(refreshToken.value()));
        verify(graceRepository).save(sha256(TOKEN), 7L, REUSE_GRACE);
    }

    @Test
    void rotatedTokenWithinGrace_rotate_returnsAccessOnlyWithoutRotating() throws Exception {
        AuthSessionRepository repository = mock(AuthSessionRepository.class);
        RefreshTokenReuseGraceRepository graceRepository = mock(RefreshTokenReuseGraceRepository.class);
        AuthSessionService service = createService(repository, graceRepository);
        AuthSession session = session(7L);
        byte[] currentHash = session.getRefreshTokenHash();
        when(repository.findByRefreshTokenHash(any())).thenReturn(Optional.empty());
        when(graceRepository.findSessionId(sha256(TOKEN))).thenReturn(Optional.of(7L));
        when(repository.findById(7L)).thenReturn(Optional.of(session));

        AuthSessionService.RefreshToken refreshToken = service.rotate(TOKEN);

        assertThat(refreshToken.value()).isNull();
        assertThat(refreshToken.userId()).isEqualTo(1L);
        assertThat(session.getRefreshTokenHash()).isEqualTo(currentHash);
        verify(graceRepository, never()).save(any(), any(), any());
    }

    @Test
    void rotatedTokenAfterGrace_rotate_throwsInvalidRefreshToken() {
        AuthSessionRepository repository = mock(AuthSessionRepository.class);
        RefreshTokenReuseGraceRepository graceRepository = mock(RefreshTokenReuseGraceRepository.class);
        AuthSessionService service = createService(repository, graceRepository);
        when(repository.findByRefreshTokenHash(any())).thenReturn(Optional.empty());
        when(graceRepository.findSessionId(any())).thenReturn(Optional.empty());

        assertInvalidRefreshToken(service);
    }

    @Test
    void revokedSessionWithinGrace_rotate_throwsInvalidRefreshToken() {
        AuthSessionRepository repository = mock(AuthSessionRepository.class);
        RefreshTokenReuseGraceRepository graceRepository = mock(RefreshTokenReuseGraceRepository.class);
        AuthSessionService service = createService(repository, graceRepository);
        AuthSession session = session(7L);
        session.revoke(RevokeReason.LOGOUT);
        when(repository.findByRefreshTokenHash(any())).thenReturn(Optional.empty());
        when(graceRepository.findSessionId(any())).thenReturn(Optional.of(7L));
        when(repository.findById(7L)).thenReturn(Optional.of(session));

        assertInvalidRefreshToken(service);
    }

    @Test
    void redisUnavailable_rotate_stillRotatesCurrentToken() {
        AuthSessionRepository repository = mock(AuthSessionRepository.class);
        RefreshTokenReuseGraceRepository graceRepository = mock(RefreshTokenReuseGraceRepository.class);
        AuthSessionService service = createService(repository, graceRepository);
        when(repository.findByRefreshTokenHash(any())).thenReturn(Optional.of(session(7L)));
        doThrow(new RedisConnectionFailureException("down")).when(graceRepository).save(any(), any(), any());

        assertThat(service.rotate(TOKEN).value()).isNotNull();
    }

    @Test
    void redisUnavailableForRotatedToken_rotate_throwsInvalidRefreshToken() {
        AuthSessionRepository repository = mock(AuthSessionRepository.class);
        RefreshTokenReuseGraceRepository graceRepository = mock(RefreshTokenReuseGraceRepository.class);
        AuthSessionService service = createService(repository, graceRepository);
        when(repository.findByRefreshTokenHash(any())).thenReturn(Optional.empty());
        when(graceRepository.findSessionId(any())).thenThrow(new RedisConnectionFailureException("down"));

        assertInvalidRefreshToken(service);
    }

    private void assertInvalidRefreshToken(AuthSessionService service) {
        assertThatThrownBy(() -> service.rotate(TOKEN))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AuthErrorCode.AUTH_REFRESH_TOKEN_INVALID);
    }

    private AuthSessionService createService(AuthSessionRepository repository,
                                             RefreshTokenReuseGraceRepository graceRepository) {
        return new AuthSessionService(
                repository, graceRepository, mock(UserService.class), Duration.ofDays(14), REUSE_GRACE);
    }

    private AuthSession session(Long id) {
        AuthSession session = new AuthSession(1L, new byte[32], LocalDateTime.now().plusDays(14));
        ReflectionTestUtils.setField(session, "id", id);
        return session;
    }

    private byte[] sha256(String token) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII));
    }
}
