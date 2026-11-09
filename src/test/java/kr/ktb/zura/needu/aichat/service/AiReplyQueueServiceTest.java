package kr.ktb.zura.needu.aichat.service;

import java.time.Duration;
import java.util.List;

import kr.ktb.zura.needu.aichat.config.AiReplyQueueProperties;
import kr.ktb.zura.needu.aichat.repository.AiReplyPermitRepository;
import kr.ktb.zura.needu.aichat.repository.AiReplyStreamClaimResult;
import kr.ktb.zura.needu.aichat.repository.AiReplyStreamEntry;
import kr.ktb.zura.needu.aichat.repository.AiReplyStreamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;

import static org.assertj.core.api.Assertions.assertThat;
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
    private static final String CONSUMER = "worker-a";
    private static final Duration CLAIM_MIN_IDLE = Duration.ofSeconds(30);

    @Mock
    private AiReplyStreamRepository aiReplyStreamRepository;

    @Mock
    private AiReplyPermitRepository aiReplyPermitRepository;

    @Mock
    private AiReplyJobService aiReplyJobService;

    private AiReplyQueueService aiReplyQueueService;

    @BeforeEach
    void setUp() {
        AiReplyQueueProperties properties = new AiReplyQueueProperties(true, 96, Duration.ofSeconds(30), 3,
                Duration.ofSeconds(5), CLAIM_MIN_IDLE, Duration.ofSeconds(5), Duration.ofMillis(200), Duration.ofSeconds(2));
        aiReplyQueueService = new AiReplyQueueService(
                aiReplyStreamRepository, aiReplyPermitRepository, aiReplyJobService, properties);
    }

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

    @Test
    void idleEntryExists_claimIdle_returnsClaimedEntry() {
        AiReplyStreamEntry entry = new AiReplyStreamEntry(STREAM_ENTRY_ID, JOB_ID, DISPATCH_TOKEN, 2);
        given(aiReplyStreamRepository.claimIdle(CONSUMER, CLAIM_MIN_IDLE))
                .willReturn(new AiReplyStreamClaimResult(entry, List.of()));

        assertThat(aiReplyQueueService.claimIdle(CONSUMER)).contains(entry);
    }

    @Test
    void noIdleEntry_claimIdle_returnsEmpty() {
        given(aiReplyStreamRepository.claimIdle(CONSUMER, CLAIM_MIN_IDLE))
                .willReturn(new AiReplyStreamClaimResult(null, List.of("1730000000000-1")));

        assertThat(aiReplyQueueService.claimIdle(CONSUMER)).isEmpty();
    }
}
