package kr.ktb.zura.needu.auth.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import kr.ktb.zura.needu.auth.repository.AuthSessionRepository;
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
}
