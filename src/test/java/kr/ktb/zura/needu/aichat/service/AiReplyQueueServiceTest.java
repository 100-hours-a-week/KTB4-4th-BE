package kr.ktb.zura.needu.aichat.service;

import kr.ktb.zura.needu.aichat.repository.AiReplyStreamRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AiReplyQueueServiceTest {

    private static final Long JOB_ID = 301L;
    private static final String DISPATCH_TOKEN = "dispatch-token";
    private static final String STREAM_ENTRY_ID = "1730000000000-0";

    @Mock
    private AiReplyStreamRepository aiReplyStreamRepository;

    @Mock
    private AiReplyJobService aiReplyJobService;

    @InjectMocks
    private AiReplyQueueService aiReplyQueueService;

    @Test
    void streamAvailable_enqueue_addsJobAndRecordsStreamEntry() {
        given(aiReplyStreamRepository.add(JOB_ID, DISPATCH_TOKEN)).willReturn(STREAM_ENTRY_ID);

        aiReplyQueueService.enqueue(JOB_ID, DISPATCH_TOKEN);

        verify(aiReplyJobService).recordStreamEntry(JOB_ID, DISPATCH_TOKEN, STREAM_ENTRY_ID);
    }

    @Test
    void streamUnavailable_enqueue_doesNotThrowOrRecord() {
        given(aiReplyStreamRepository.add(JOB_ID, DISPATCH_TOKEN))
                .willThrow(new RedisConnectionFailureException("redis down"));

        assertThatCode(() -> aiReplyQueueService.enqueue(JOB_ID, DISPATCH_TOKEN)).doesNotThrowAnyException();
        verify(aiReplyJobService, never()).recordStreamEntry(any(), any(), any());
    }

    @Test
    void streamEntryRecordFails_enqueue_doesNotThrow() {
        given(aiReplyStreamRepository.add(JOB_ID, DISPATCH_TOKEN)).willReturn(STREAM_ENTRY_ID);
        willThrow(new IllegalStateException("db down"))
                .given(aiReplyJobService).recordStreamEntry(JOB_ID, DISPATCH_TOKEN, STREAM_ENTRY_ID);

        assertThatCode(() -> aiReplyQueueService.enqueue(JOB_ID, DISPATCH_TOKEN)).doesNotThrowAnyException();
    }
}
