package kr.ktb.zura.needu.aichat.service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import kr.ktb.zura.needu.aichat.config.AiReplyQueueProperties;
import kr.ktb.zura.needu.aichat.entity.AiChatRoom;
import kr.ktb.zura.needu.aichat.entity.AiReplyJob;
import kr.ktb.zura.needu.aichat.repository.AiReplyPermitRepository;
import kr.ktb.zura.needu.aichat.repository.AiReplyStreamClaimResult;
import kr.ktb.zura.needu.aichat.repository.AiReplyStreamEntry;
import kr.ktb.zura.needu.aichat.repository.AiReplyStreamRepository;
import kr.ktb.zura.needu.aichat.type.AiReplyJobStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.test.util.ReflectionTestUtils;

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
    private static final Duration STALE_AFTER = Duration.ofSeconds(15);
    private static final Long OTHER_JOB_ID = 302L;
    private static final String NEW_DISPATCH_TOKEN = "new-dispatch-token";
    private static final String NEW_STREAM_ENTRY_ID = "1730000000100-0";

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

    @Test
    void queuedJobWithoutStreamEntry_redispatchLostJobs_enqueuesWithNewDispatchToken() {
        AiReplyJob job = queuedJob(JOB_ID, null);
        given(aiReplyJobService.findAllStaleJobs(STALE_AFTER)).willReturn(List.of(job));
        given(aiReplyJobService.redispatch(JOB_ID, job.getDispatchToken(), AiReplyJobStatus.QUEUED))
                .willReturn(Optional.of(NEW_DISPATCH_TOKEN));
        given(aiReplyStreamRepository.add(JOB_ID, NEW_DISPATCH_TOKEN)).willReturn(NEW_STREAM_ENTRY_ID);

        aiReplyQueueService.redispatchLostJobs(STALE_AFTER);

        verify(aiReplyJobService).recordStreamEntry(JOB_ID, NEW_DISPATCH_TOKEN, NEW_STREAM_ENTRY_ID);
    }

    @Test
    void queuedJobMissingFromStream_redispatchLostJobs_enqueuesWithNewDispatchToken() {
        AiReplyJob job = queuedJob(JOB_ID, STREAM_ENTRY_ID);
        given(aiReplyJobService.findAllStaleJobs(STALE_AFTER)).willReturn(List.of(job));
        given(aiReplyStreamRepository.exists(STREAM_ENTRY_ID)).willReturn(false);
        given(aiReplyJobService.redispatch(JOB_ID, job.getDispatchToken(), AiReplyJobStatus.QUEUED))
                .willReturn(Optional.of(NEW_DISPATCH_TOKEN));
        given(aiReplyStreamRepository.add(JOB_ID, NEW_DISPATCH_TOKEN)).willReturn(NEW_STREAM_ENTRY_ID);

        aiReplyQueueService.redispatchLostJobs(STALE_AFTER);

        verify(aiReplyJobService).recordStreamEntry(JOB_ID, NEW_DISPATCH_TOKEN, NEW_STREAM_ENTRY_ID);
    }

    @Test
    void queuedJobWaitingInStream_redispatchLostJobs_keepsJob() {
        AiReplyJob job = queuedJob(JOB_ID, STREAM_ENTRY_ID);
        given(aiReplyJobService.findAllStaleJobs(STALE_AFTER)).willReturn(List.of(job));
        given(aiReplyStreamRepository.exists(STREAM_ENTRY_ID)).willReturn(true);

        aiReplyQueueService.redispatchLostJobs(STALE_AFTER);

        verify(aiReplyJobService, never()).redispatch(any(), any(), any());
        verify(aiReplyStreamRepository, never()).add(any(), any());
    }

    @Test
    void runningJobMissingFromPending_redispatchLostJobs_enqueuesWithNewDispatchToken() {
        AiReplyJob job = runningJob(JOB_ID, STREAM_ENTRY_ID);
        given(aiReplyJobService.findAllStaleJobs(STALE_AFTER)).willReturn(List.of(job));
        given(aiReplyStreamRepository.isPending(STREAM_ENTRY_ID)).willReturn(false);
        given(aiReplyJobService.redispatch(JOB_ID, job.getDispatchToken(), AiReplyJobStatus.RUNNING))
                .willReturn(Optional.of(NEW_DISPATCH_TOKEN));
        given(aiReplyStreamRepository.add(JOB_ID, NEW_DISPATCH_TOKEN)).willReturn(NEW_STREAM_ENTRY_ID);

        aiReplyQueueService.redispatchLostJobs(STALE_AFTER);

        verify(aiReplyJobService).recordStreamEntry(JOB_ID, NEW_DISPATCH_TOKEN, NEW_STREAM_ENTRY_ID);
    }

    @Test
    void runningJobPending_redispatchLostJobs_keepsJob() {
        AiReplyJob job = runningJob(JOB_ID, STREAM_ENTRY_ID);
        given(aiReplyJobService.findAllStaleJobs(STALE_AFTER)).willReturn(List.of(job));
        given(aiReplyStreamRepository.isPending(STREAM_ENTRY_ID)).willReturn(true);

        aiReplyQueueService.redispatchLostJobs(STALE_AFTER);

        verify(aiReplyJobService, never()).redispatch(any(), any(), any());
    }

    // 플래그를 켜는 동안 아직 받은 서버가 바로 처리하는 작업이 남아 있을 수 있다
    @Test
    void runningJobWithoutStreamEntry_redispatchLostJobs_keepsJobWithoutCheckingStream() {
        given(aiReplyJobService.findAllStaleJobs(STALE_AFTER)).willReturn(List.of(runningJob(JOB_ID, null)));

        aiReplyQueueService.redispatchLostJobs(STALE_AFTER);

        verify(aiReplyStreamRepository, never()).isPending(any());
        verify(aiReplyJobService, never()).redispatch(any(), any(), any());
    }

    @Test
    void jobChangedAfterLookup_redispatchLostJobs_doesNotEnqueue() {
        AiReplyJob job = runningJob(JOB_ID, STREAM_ENTRY_ID);
        given(aiReplyJobService.findAllStaleJobs(STALE_AFTER)).willReturn(List.of(job));
        given(aiReplyStreamRepository.isPending(STREAM_ENTRY_ID)).willReturn(false);
        given(aiReplyJobService.redispatch(JOB_ID, job.getDispatchToken(), AiReplyJobStatus.RUNNING))
                .willReturn(Optional.empty());

        aiReplyQueueService.redispatchLostJobs(STALE_AFTER);

        verify(aiReplyStreamRepository, never()).add(any(), any());
    }

    @Test
    void streamUnavailable_redispatchLostJobs_stopsWithoutThrowing() {
        AiReplyJob job = queuedJob(JOB_ID, STREAM_ENTRY_ID);
        AiReplyJob otherJob = queuedJob(OTHER_JOB_ID, "1730000000001-0");
        given(aiReplyJobService.findAllStaleJobs(STALE_AFTER)).willReturn(List.of(job, otherJob));
        given(aiReplyStreamRepository.exists(STREAM_ENTRY_ID))
                .willThrow(new RedisConnectionFailureException("redis down"));

        assertThatCode(() -> aiReplyQueueService.redispatchLostJobs(STALE_AFTER)).doesNotThrowAnyException();
        verify(aiReplyStreamRepository, never()).exists("1730000000001-0");
        verify(aiReplyJobService, never()).redispatch(any(), any(), any());
    }

    private static AiReplyJob queuedJob(Long jobId, String streamEntryId) {
        AiReplyJob job = AiReplyJob.create(AiChatRoom.reserve(1L), 201L, 1L, "lock-token");
        ReflectionTestUtils.setField(job, "id", jobId);
        ReflectionTestUtils.setField(job, "streamEntryId", streamEntryId);
        return job;
    }

    private static AiReplyJob runningJob(Long jobId, String streamEntryId) {
        AiReplyJob job = queuedJob(jobId, null);
        job.take(streamEntryId);
        return job;
    }
}
