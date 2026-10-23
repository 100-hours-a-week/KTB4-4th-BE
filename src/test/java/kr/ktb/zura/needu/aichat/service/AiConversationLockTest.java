package kr.ktb.zura.needu.aichat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import java.util.Optional;
import kr.ktb.zura.needu.aichat.repository.AiConversationLockRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;

@ExtendWith(MockitoExtension.class)
class AiConversationLockTest {

    private static final Long CONVERSATION_ID = 101L;

    @Mock
    private AiConversationLockRepository lockRepository;

    @InjectMocks
    private AiConversationLock aiConversationLock;

    @Test
    void lockAvailable_tryLock_returnsHandleWithStoredOwnerToken() {
        given(lockRepository.create(eq(CONVERSATION_ID), anyString())).willReturn(true);

        Optional<AiConversationLock.Handle> handle = aiConversationLock.tryLock(CONVERSATION_ID);

        ArgumentCaptor<String> ownerToken = ArgumentCaptor.forClass(String.class);
        verify(lockRepository).create(eq(CONVERSATION_ID), ownerToken.capture());
        assertThat(handle).contains(new AiConversationLock.Handle(CONVERSATION_ID, ownerToken.getValue()));
    }

    @Test
    void lockHeldByAnotherRequest_tryLock_returnsEmpty() {
        given(lockRepository.create(eq(CONVERSATION_ID), anyString())).willReturn(false);

        Optional<AiConversationLock.Handle> handle = aiConversationLock.tryLock(CONVERSATION_ID);

        assertThat(handle).isEmpty();
    }

    @Test
    void lockHeld_isLocked_returnsTrue() {
        given(lockRepository.exists(CONVERSATION_ID)).willReturn(true);

        assertThat(aiConversationLock.isLocked(CONVERSATION_ID)).isTrue();
    }

    @Test
    void lockReleased_isLocked_returnsFalse() {
        given(lockRepository.exists(CONVERSATION_ID)).willReturn(false);

        assertThat(aiConversationLock.isLocked(CONVERSATION_ID)).isFalse();
    }

    @Test
    void eachTryLock_usesDifferentOwnerToken() {
        given(lockRepository.create(eq(CONVERSATION_ID), anyString())).willReturn(true);

        AiConversationLock.Handle first = aiConversationLock.tryLock(CONVERSATION_ID).orElseThrow();
        AiConversationLock.Handle second = aiConversationLock.tryLock(CONVERSATION_ID).orElseThrow();

        assertThat(first.ownerToken()).isNotEqualTo(second.ownerToken());
    }

    @Test
    void heldLock_unlock_deletesWithOwnerToken() {
        AiConversationLock.Handle handle = new AiConversationLock.Handle(CONVERSATION_ID, "owner-token");

        aiConversationLock.unlock(handle);

        verify(lockRepository).delete(CONVERSATION_ID, "owner-token");
    }

    @Test
    void redisUnavailable_unlock_doesNotThrow() {
        AiConversationLock.Handle handle = new AiConversationLock.Handle(CONVERSATION_ID, "owner-token");
        willThrow(new RedisConnectionFailureException("redis down"))
                .given(lockRepository).delete(CONVERSATION_ID, "owner-token");

        assertThatCode(() -> aiConversationLock.unlock(handle)).doesNotThrowAnyException();
    }
}
