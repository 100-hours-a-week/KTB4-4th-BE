package kr.ktb.zura.needu.auth.service;

import java.time.Duration;
import java.util.Optional;
import kr.ktb.zura.needu.auth.repository.KakaoOAuthTransactionRepository;
import kr.ktb.zura.needu.auth.type.KakaoOAuthContext;
import kr.ktb.zura.needu.auth.type.KakaoOAuthFlow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KakaoOAuthTransactionServiceTest {

    private KakaoOAuthTransactionRepository repository;
    private KakaoOAuthTransactionService service;

    @BeforeEach
    void setUp() {
        repository = mock(KakaoOAuthTransactionRepository.class);
        service = new KakaoOAuthTransactionService(repository, Duration.ofMinutes(10));
    }

    @Test
    void loginStateCreated_savesContextWithRandomStateAndTtl() {
        String state = service.createLoginState("https://needu.example.com/login");

        ArgumentCaptor<String> stateCaptor = ArgumentCaptor.forClass(String.class);
        verify(repository).save(eq(KakaoOAuthFlow.LOGIN), stateCaptor.capture(),
                eq(new KakaoOAuthContext("https://needu.example.com/login", null)),
                eq(Duration.ofMinutes(10)));
        assertThat(state).isEqualTo(stateCaptor.getValue()).isNotBlank();
    }

    @Test
    void matchingBrowserState_consumeLogin_returnsContext() {
        when(repository.consume(KakaoOAuthFlow.LOGIN, "state-123"))
                .thenReturn(Optional.of(new KakaoOAuthContext("https://needu.example.com/login", null)));

        Optional<KakaoOAuthContext> context =
                service.consumeLoginState("state-123", "state-123");

        assertThat(context).contains(new KakaoOAuthContext(
                "https://needu.example.com/login", null));
    }

    @Test
    void mismatchedBrowserState_consumeFriend_doesNotConsumeTransaction() {
        Optional<KakaoOAuthContext> context =
                service.consumeFriendState("state-123", "different-state");
        Optional<KakaoOAuthContext> blankContext =
                service.consumeFriendState("state-123", " ");

        assertThat(context).isEmpty();
        assertThat(blankContext).isEmpty();
        verify(repository, never()).consume(KakaoOAuthFlow.FRIEND, "state-123");
    }
}
