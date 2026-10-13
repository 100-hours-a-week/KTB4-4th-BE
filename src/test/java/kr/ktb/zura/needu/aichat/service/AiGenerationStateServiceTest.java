package kr.ktb.zura.needu.aichat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import java.util.Optional;
import kr.ktb.zura.needu.aichat.repository.AiGenerationStateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;

@ExtendWith(MockitoExtension.class)
class AiGenerationStateServiceTest {

    private static final Long CONVERSATION_ID = 101L;
    private static final Long USER_MESSAGE_ID = 701L;

    @Mock
    private AiGenerationStateRepository aiGenerationStateRepository;

    @InjectMocks
    private AiGenerationStateService aiGenerationStateService;

    @Test
    void replyRequested_startGenerating_savesUserMessageId() {
        aiGenerationStateService.startGenerating(CONVERSATION_ID, USER_MESSAGE_ID);

        verify(aiGenerationStateRepository).save(CONVERSATION_ID, USER_MESSAGE_ID);
    }

    @Test
    void replyFinished_finishGenerating_deletesOnlyThatMessageState() {
        aiGenerationStateService.finishGenerating(CONVERSATION_ID, USER_MESSAGE_ID);

        verify(aiGenerationStateRepository).delete(CONVERSATION_ID, USER_MESSAGE_ID);
    }

    @Test
    void redisUnavailable_finishGenerating_doesNotThrow() {
        willThrow(new RedisConnectionFailureException("redis down"))
                .given(aiGenerationStateRepository).delete(CONVERSATION_ID, USER_MESSAGE_ID);

        assertThatCode(() -> aiGenerationStateService.finishGenerating(CONVERSATION_ID, USER_MESSAGE_ID))
                .doesNotThrowAnyException();
    }

    @Test
    void sameMessageGenerating_isGenerating_returnsTrue() {
        given(aiGenerationStateRepository.findUserMessageId(CONVERSATION_ID)).willReturn(Optional.of(USER_MESSAGE_ID));

        assertThat(aiGenerationStateService.isGenerating(CONVERSATION_ID, USER_MESSAGE_ID)).isTrue();
    }

    @Test
    void anotherMessageGenerating_isGenerating_returnsFalse() {
        given(aiGenerationStateRepository.findUserMessageId(CONVERSATION_ID)).willReturn(Optional.of(702L));

        assertThat(aiGenerationStateService.isGenerating(CONVERSATION_ID, USER_MESSAGE_ID)).isFalse();
    }

    @Test
    void stateExpiredAfterServerStopped_isGenerating_returnsFalse() {
        given(aiGenerationStateRepository.findUserMessageId(CONVERSATION_ID)).willReturn(Optional.empty());

        assertThat(aiGenerationStateService.isGenerating(CONVERSATION_ID, USER_MESSAGE_ID)).isFalse();
    }

    @Test
    void generationInProgress_findGeneratingUserMessageId_returnsMessageId() {
        given(aiGenerationStateRepository.findUserMessageId(CONVERSATION_ID)).willReturn(Optional.of(USER_MESSAGE_ID));

        assertThat(aiGenerationStateService.findGeneratingUserMessageId(CONVERSATION_ID)).contains(USER_MESSAGE_ID);
    }
}
