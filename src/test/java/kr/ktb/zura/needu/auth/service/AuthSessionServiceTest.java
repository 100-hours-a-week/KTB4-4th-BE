package kr.ktb.zura.needu.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import kr.ktb.zura.needu.auth.entity.AuthSession;
import kr.ktb.zura.needu.auth.repository.AuthSessionRepository;
import kr.ktb.zura.needu.auth.type.RevokeReason;
import kr.ktb.zura.needu.user.service.UserService;
import org.junit.jupiter.api.Test;

class AuthSessionServiceTest {

    @Test
    void existingSessions_deleteAllByUserId_deletesEverySession() {
        AuthSessionRepository repository = mock(AuthSessionRepository.class);
        AuthSessionService service = new AuthSessionService(
                repository,
                mock(UserService.class),
                Duration.ofDays(14));

        service.deleteAllByUserId(1L);

        verify(repository).deleteAllByUserId(1L);
    }

    @Test
    void activeSessions_revokeAllByUserId_revokesEverySession() {
        AuthSessionRepository repository = mock(AuthSessionRepository.class);
        AuthSessionService service = new AuthSessionService(
                repository,
                mock(UserService.class),
                Duration.ofDays(14));
        AuthSession first = new AuthSession(1L, new byte[]{1}, LocalDateTime.now().plusDays(14));
        AuthSession second = new AuthSession(1L, new byte[]{2}, LocalDateTime.now().plusDays(14));
        when(repository.findAllByUserIdAndRevokedAtIsNull(1L)).thenReturn(List.of(first, second));

        service.revokeAllByUserId(1L, RevokeReason.BLOCKED);

        assertThat(List.of(first, second)).allSatisfy(session -> {
            assertThat(session.isRevoked()).isTrue();
            assertThat(session.getRevokeReason()).isEqualTo(RevokeReason.BLOCKED);
        });
    }
}
