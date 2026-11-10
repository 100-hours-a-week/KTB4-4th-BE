package kr.ktb.zura.needu.aichat.facade;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import kr.ktb.zura.needu.aichat.config.AiReplyConsumerName;
import kr.ktb.zura.needu.aichat.config.AiReplyQueueProperties;
import kr.ktb.zura.needu.aichat.repository.AiReplyHeartbeatResult;
import kr.ktb.zura.needu.aichat.repository.AiReplyStreamEntry;
import kr.ktb.zura.needu.aichat.service.AiConversationLock;
import kr.ktb.zura.needu.aichat.service.AiGenerationStateService;
import kr.ktb.zura.needu.aichat.service.AiReplyQueueService;
import kr.ktb.zura.needu.aichat.service.AiReplyTaskExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AiReplyWorkerTest {

    private static final String CONSUMER = "worker-a";
    private static final String PERMIT_ID = "permit-1";
    private static final Long JOB_ID = 301L;
    private static final Long CONVERSATION_ID = 101L;
    private static final Long USER_MESSAGE_ID = 201L;
    private static final String DISPATCH_TOKEN = "dispatch-token";
    private static final String LOCK_TOKEN = "lock-token";
    private static final String STREAM_ENTRY_ID = "1730000000000-0";
    private static final AiReplyStreamEntry ENTRY = new AiReplyStreamEntry(STREAM_ENTRY_ID, JOB_ID, DISPATCH_TOKEN, 1);
    private static final Instant NOW = Instant.parse("2026-11-07T12:00:00Z");
    private static final Duration CLAIM_MIN_IDLE = Duration.ofSeconds(30);
    private static final Duration CLAIM_INTERVAL = Duration.ofSeconds(5);
    // process가 띄우는 하트비트 스레드가 테스트 중에 돌지 않게 길게 둔다
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofHours(1);
    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofMillis(400);

    @Mock
    private AiChatFacade aiChatFacade;

    @Mock
    private AiReplyQueueService aiReplyQueueService;

    @Mock
    private AiReplyTaskExecutor aiReplyTaskExecutor;

    @Mock
    private AiReplyTaskExecutor.Slot slot;

    @Mock
    private AiConversationLock aiConversationLock;

    @Mock
    private AiGenerationStateService aiGenerationStateService;

    private AiReplyLeaseTest.MutableClock clock;
    private AiReplyWorker worker;

    @BeforeEach
    void setUp() {
        clock = new AiReplyLeaseTest.MutableClock(NOW);
        worker = createWorker(true);
    }

    @Test
    void replyQueueDisabled_start_doesNotRunWorker() {
        AiReplyWorker disabledWorker = createWorker(false);

        disabledWorker.start();

        assertThat(disabledWorker.isRunning()).isFalse();
        verifyNoInteractions(aiReplyQueueService);
    }

    @Test
    void noLocalSlot_dispatchNext_doesNotTakePermitOrRead() {
        given(aiReplyTaskExecutor.tryReserve()).willReturn(Optional.empty());

        assertThat(worker.dispatchNext()).isFalse();
        verifyNoInteractions(aiReplyQueueService);
    }

    @Test
    void noGlobalPermit_dispatchNext_releasesSlotWithoutReading() {
        given(aiReplyTaskExecutor.tryReserve()).willReturn(Optional.of(slot));
        given(aiReplyQueueService.tryAcquirePermit(anyString())).willReturn(false);

        assertThat(worker.dispatchNext()).isFalse();
        verify(slot).release();
        verify(aiReplyQueueService, never()).readNew(any());
    }

    @Test
    void noEntry_dispatchNext_releasesPermitAndSlot() {
        givenSlotAndPermit();
        given(aiReplyQueueService.claimIdle(CONSUMER)).willReturn(Optional.empty());
        given(aiReplyQueueService.readNew(CONSUMER)).willReturn(Optional.empty());

        assertThat(worker.dispatchNext()).isFalse();
        verify(aiReplyQueueService).releasePermit(anyString());
        verify(slot).release();
    }

    @Test
    void idleEntryClaimed_dispatchNext_runsClaimedEntryWithoutReadingNew() {
        givenSlotAndPermit();
        given(aiReplyQueueService.claimIdle(CONSUMER)).willReturn(Optional.of(ENTRY));

        assertThat(worker.dispatchNext()).isTrue();
        verify(slot).execute(any());
        verify(aiReplyQueueService, never()).readNew(any());
        verify(aiReplyQueueService, never()).releasePermit(anyString());
    }

    @Test
    void claimCheckedRecently_dispatchNext_readsNewEntryWithoutClaim() {
        givenSlotAndPermit();
        given(aiReplyQueueService.claimIdle(CONSUMER)).willReturn(Optional.empty());
        given(aiReplyQueueService.readNew(CONSUMER)).willReturn(Optional.empty(), Optional.of(ENTRY));
        worker.dispatchNext();

        clock.advance(CLAIM_INTERVAL.minusSeconds(1));

        assertThat(worker.dispatchNext()).isTrue();
        verify(aiReplyQueueService).claimIdle(CONSUMER);
        verify(slot).execute(any());
    }

    @Test
    void jobFinished_process_acknowledgesEntryAndReleasesPermit() {
        given(aiChatFacade.replyToJob(any(), any(), anyLong(), any(), any()))
                .willReturn(AiReplyJobOutcome.FINISHED);

        worker.process(ENTRY, PERMIT_ID, AiReplyLease.start(clock));

        verify(aiChatFacade).replyToJob(any(), any(), anyLong(), any(), any());
        verify(aiReplyQueueService).acknowledge(STREAM_ENTRY_ID);
        verify(aiReplyQueueService).releasePermit(PERMIT_ID);
    }

    @Test
    void jobReturnedToQueue_process_acknowledgesOldEntryAndReleasesPermit() {
        given(aiChatFacade.replyToJob(any(), any(), anyLong(), any(), any()))
                .willReturn(AiReplyJobOutcome.RETURNED);

        worker.process(ENTRY, PERMIT_ID, AiReplyLease.start(clock));

        verify(aiReplyQueueService).acknowledge(STREAM_ENTRY_ID);
        verify(aiReplyQueueService).releasePermit(PERMIT_ID);
    }

    @Test
    void jobFinishesWithinShutdownTimeout_stop_waitsForJobWithoutRequestingReturn() {
        givenEntryRunsOnNewThread();
        given(aiChatFacade.replyToJob(any(), any(), anyLong(), any(), any())).willAnswer(invocation -> {
            Thread.sleep(SHUTDOWN_TIMEOUT.dividedBy(4));
            return AiReplyJobOutcome.FINISHED;
        });
        worker.dispatchNext();

        worker.stop();

        verify(aiReplyQueueService).acknowledge(STREAM_ENTRY_ID);
        assertThat(capturedLease().isReturning()).isFalse();
    }

    @Test
    void jobRunningPastShutdownTimeout_stop_interruptsJobAndAcknowledgesReturnedEntry() {
        givenEntryRunsOnNewThread();
        given(aiChatFacade.replyToJob(any(), any(), anyLong(), any(), any())).willAnswer(invocation -> {
            try {
                Thread.sleep(Duration.ofMinutes(1));
                return AiReplyJobOutcome.FINISHED;
            } catch (InterruptedException e) {
                return invocation.<AiReplyLease>getArgument(4).isReturning()
                        ? AiReplyJobOutcome.RETURNED
                        : AiReplyJobOutcome.OWNERSHIP_LOST;
            }
        });
        worker.dispatchNext();

        worker.stop();

        assertThat(capturedLease().isReturning()).isTrue();
        verify(aiReplyQueueService).acknowledge(STREAM_ENTRY_ID);
        verify(aiReplyQueueService).releasePermit(anyString());
    }

    @Test
    void jobTakenByAnotherWorker_process_leavesEntryAndReleasesPermit() {
        given(aiChatFacade.replyToJob(any(), any(), anyLong(), any(), any()))
                .willReturn(AiReplyJobOutcome.OWNERSHIP_LOST);

        worker.process(ENTRY, PERMIT_ID, AiReplyLease.start(clock));

        verify(aiReplyQueueService, never()).acknowledge(any());
        verify(aiReplyQueueService).releasePermit(PERMIT_ID);
    }

    @Test
    void processingFails_process_leavesEntryForClaimAndReleasesPermit() {
        given(aiChatFacade.replyToJob(any(), any(), anyLong(), any(), any()))
                .willThrow(new IllegalStateException("db down"));

        worker.process(ENTRY, PERMIT_ID, AiReplyLease.start(clock));

        verify(aiReplyQueueService, never()).acknowledge(any());
        verify(aiReplyQueueService).releasePermit(PERMIT_ID);
    }

    @Test
    void malformedEntry_process_deletesEntryWithoutProcessing() {
        AiReplyStreamEntry malformed = new AiReplyStreamEntry(STREAM_ENTRY_ID, null, null, 1);

        worker.process(malformed, PERMIT_ID, AiReplyLease.start(clock));

        verifyNoInteractions(aiChatFacade);
        verify(aiReplyQueueService).acknowledge(STREAM_ENTRY_ID);
        verify(aiReplyQueueService).releasePermit(PERMIT_ID);
    }

    @Test
    void stillOwnedAfterJobTaken_extendLease_extendsPermitLockAndGeneratingState() {
        AiReplyLease lease = AiReplyLease.start(clock);
        lease.attach(CONVERSATION_ID, USER_MESSAGE_ID, LOCK_TOKEN);
        given(aiReplyQueueService.heartbeat(CONSUMER, STREAM_ENTRY_ID)).willReturn(AiReplyHeartbeatResult.OWNED);
        clock.advance(CLAIM_MIN_IDLE);

        worker.extendLease(ENTRY, PERMIT_ID, lease);

        assertThat(lease.isLost()).isFalse();
        assertThat(lease.isConfirmedWithin(Duration.ZERO)).isTrue();
        verify(aiReplyQueueService).extendPermit(PERMIT_ID);
        verify(aiConversationLock).ensure(new AiConversationLock.Handle(CONVERSATION_ID, LOCK_TOKEN));
        verify(aiGenerationStateService).extendGenerating(CONVERSATION_ID, USER_MESSAGE_ID);
    }

    @Test
    void claimedByAnotherWorker_extendLease_marksLeaseLostWithoutExtending() {
        AiReplyLease lease = AiReplyLease.start(clock);
        given(aiReplyQueueService.heartbeat(CONSUMER, STREAM_ENTRY_ID))
                .willReturn(AiReplyHeartbeatResult.CLAIMED_BY_OTHER);

        worker.extendLease(ENTRY, PERMIT_ID, lease);

        assertThat(lease.isLost()).isTrue();
        verify(aiReplyQueueService, never()).extendPermit(any());
    }

    @Test
    void entryNoLongerPending_extendLease_marksLeaseLost() {
        AiReplyLease lease = AiReplyLease.start(clock);
        given(aiReplyQueueService.heartbeat(CONSUMER, STREAM_ENTRY_ID)).willReturn(AiReplyHeartbeatResult.NOT_PENDING);

        worker.extendLease(ENTRY, PERMIT_ID, lease);

        assertThat(lease.isLost()).isTrue();
    }

    @Test
    void redisUnavailableWithinClaimMinIdle_extendLease_keepsLease() {
        AiReplyLease lease = AiReplyLease.start(clock);
        given(aiReplyQueueService.heartbeat(CONSUMER, STREAM_ENTRY_ID))
                .willThrow(new RedisConnectionFailureException("redis down"));
        clock.advance(CLAIM_MIN_IDLE);

        worker.extendLease(ENTRY, PERMIT_ID, lease);

        assertThat(lease.isLost()).isFalse();
    }

    @Test
    void redisUnavailableLongerThanClaimMinIdle_extendLease_marksLeaseLost() {
        AiReplyLease lease = AiReplyLease.start(clock);
        given(aiReplyQueueService.heartbeat(CONSUMER, STREAM_ENTRY_ID))
                .willThrow(new RedisConnectionFailureException("redis down"));
        clock.advance(CLAIM_MIN_IDLE.plusSeconds(1));

        worker.extendLease(ENTRY, PERMIT_ID, lease);

        assertThat(lease.isLost()).isTrue();
    }

    private void givenSlotAndPermit() {
        given(aiReplyTaskExecutor.tryReserve()).willReturn(Optional.of(slot));
        given(aiReplyQueueService.tryAcquirePermit(anyString())).willReturn(true);
    }

    private void givenEntryRunsOnNewThread() {
        givenSlotAndPermit();
        given(aiReplyQueueService.claimIdle(CONSUMER)).willReturn(Optional.of(ENTRY));
        willAnswer(invocation -> Thread.ofVirtual().start(invocation.<Runnable>getArgument(0)))
                .given(slot).execute(any());
    }

    private AiReplyLease capturedLease() {
        ArgumentCaptor<AiReplyLease> lease = ArgumentCaptor.forClass(AiReplyLease.class);
        verify(aiChatFacade).replyToJob(any(), any(), anyLong(), any(), lease.capture());
        return lease.getValue();
    }

    private AiReplyWorker createWorker(boolean enabled) {
        AiReplyQueueProperties properties = new AiReplyQueueProperties(enabled, 96, Duration.ofSeconds(30), 3,
                HEARTBEAT_INTERVAL, CLAIM_MIN_IDLE, CLAIM_INTERVAL, Duration.ofMillis(200), Duration.ofSeconds(2));
        return new AiReplyWorker(aiChatFacade, aiReplyQueueService, aiReplyTaskExecutor, aiConversationLock,
                aiGenerationStateService, properties, new AiReplyConsumerName(CONSUMER), clock, SHUTDOWN_TIMEOUT);
    }
}
