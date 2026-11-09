package kr.ktb.zura.needu.aichat.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import javax.sql.DataSource;

import kr.ktb.zura.needu.aichat.config.AiConversationLockProperties;
import kr.ktb.zura.needu.aichat.config.AiReplyQueueProperties;
import kr.ktb.zura.needu.aichat.entity.AiChatRoom;
import kr.ktb.zura.needu.aichat.entity.AiMessage;
import kr.ktb.zura.needu.aichat.entity.AiReplyJob;
import kr.ktb.zura.needu.aichat.repository.AiChatRoomRepository;
import kr.ktb.zura.needu.aichat.repository.AiMessageRepository;
import kr.ktb.zura.needu.aichat.repository.AiReplyJobRepository;
import kr.ktb.zura.needu.aichat.type.AiReplyJobStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 메시지·답변과 작업 행이 같은 트랜잭션으로 저장·취소되는지가 핵심이라 테스트 트랜잭션 없이 실제 JPA(H2) 위에서 검증한다
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({AiReplyJobService.class, AiMessageService.class, AiReplyJobServiceTest.Config.class})
class AiReplyJobServiceTest {

    private static final Long USER_ID = 1L;
    private static final String LOCK_TOKEN = "lock-token-1";
    private static final String NEW_LOCK_TOKEN = "lock-token-2";
    private static final String ERROR_CODE = "AICHAT_SERVER_UNAVAILABLE";
    private static final String USER_CONTENT = "요즘 러닝에 관심이 생겼어.";
    private static final String AI_CONTENT = "러닝을 좋아하시는군요.";
    private static final LocalDateTime PURGE_AT = LocalDateTime.of(2026, 11, 4, 12, 30);
    private static final LocalDateTime LONG_AGO = LocalDateTime.of(2026, 1, 1, 0, 0);
    private static final int MAX_DELIVERIES = 3;
    private static final String STREAM_ENTRY_ID = "1730000000000-0";

    @TestConfiguration
    static class Config {

        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }

        @Bean
        AiConversationLockProperties aiConversationLockProperties() {
            return new AiConversationLockProperties(Duration.ofSeconds(120), "backend:test:aichat:lock");
        }

        @Bean
        AiReplyQueueProperties aiReplyQueueProperties() {
            return new AiReplyQueueProperties(false, 96, Duration.ofSeconds(30), MAX_DELIVERIES,
                    Duration.ofSeconds(5), Duration.ofSeconds(30), Duration.ofSeconds(5), Duration.ofMillis(200),
                    Duration.ofSeconds(2));
        }
    }

    @Autowired
    private AiReplyJobService aiReplyJobService;

    @Autowired
    private AiReplyJobRepository aiReplyJobRepository;

    @Autowired
    private AiMessageRepository aiMessageRepository;

    @Autowired
    private AiMessageService aiMessageService;

    @Autowired
    private AiChatRoomRepository aiChatRoomRepository;

    @Autowired
    private DataSource dataSource;

    private Long roomId;

    @BeforeEach
    void setUp() {
        roomId = aiChatRoomRepository.save(AiChatRoom.reserve(USER_ID)).getId();
    }

    @AfterEach
    void tearDown() {
        aiReplyJobRepository.deleteAllInBatch();
        aiMessageRepository.deleteAllInBatch();
        aiChatRoomRepository.deleteAllInBatch();
    }

    @Test
    void newMessage_createUserMessageAndJob_savesMessageAndQueuedJob() {
        UUID clientMessageId = UUID.randomUUID();

        AiReplyJobDispatch dispatch = aiReplyJobService.createUserMessageAndJob(
                roomId, USER_ID, clientMessageId, USER_CONTENT, LOCK_TOKEN);

        assertThat(aiMessageRepository.findByAiChatRoomIdAndClientMessageId(roomId, clientMessageId)).isPresent();
        AiReplyJob job = findJob(dispatch.userMessage().getId());
        assertThat(dispatch.jobId()).isEqualTo(job.getId());
        assertThat(dispatch.dispatchToken()).isEqualTo(job.getDispatchToken());
        assertThat(job.getStatus()).isEqualTo(AiReplyJobStatus.QUEUED);
        assertThat(job.getUserId()).isEqualTo(USER_ID);
        assertThat(job.getLockToken()).isEqualTo(LOCK_TOKEN);
    }

    @Test
    void jobSaveFails_createUserMessageAndJob_rollsBackUserMessage() {
        UUID clientMessageId = UUID.randomUUID();

        assertThatThrownBy(() -> aiReplyJobService.createUserMessageAndJob(
                roomId, USER_ID, clientMessageId, USER_CONTENT, null))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(aiMessageRepository.findByAiChatRoomIdAndClientMessageId(roomId, clientMessageId)).isEmpty();
        assertThat(aiReplyJobRepository.count()).isZero();
    }

    @Test
    void recentlyUpdatedUnfinishedJob_isReplyInProgress_returnsTrue() {
        Long userMessageId = createUserMessageAndJob();

        assertThat(aiReplyJobService.isReplyInProgress(userMessageId)).isTrue();
    }

    @Test
    void unfinishedJobNotUpdatedWithinLockTtl_isReplyInProgress_returnsFalse() {
        Long userMessageId = takeNewJob().userMessageId();
        markUpdatedLongAgo(userMessageId);

        assertThat(aiReplyJobService.isReplyInProgress(userMessageId)).isFalse();
    }

    @Test
    void failedJob_isReplyInProgress_returnsFalse() {
        Long userMessageId = createUserMessageAndJob();
        aiReplyJobService.failJob(userMessageId, ERROR_CODE);

        assertThat(aiReplyJobService.isReplyInProgress(userMessageId)).isFalse();
    }

    @Test
    void messageWithoutJob_isReplyInProgress_returnsFalse() {
        assertThat(aiReplyJobService.isReplyInProgress(999L)).isFalse();
    }

    @Test
    void recentlyUpdatedUnfinishedJobInRoom_existsReplyInProgress_returnsTrue() {
        createUserMessageAndJob();

        assertThat(aiReplyJobService.existsReplyInProgress(roomId)).isTrue();
    }

    @Test
    void onlyAbandonedJobInRoom_existsReplyInProgress_returnsFalse() {
        Long userMessageId = takeNewJob().userMessageId();
        markUpdatedLongAgo(userMessageId);

        assertThat(aiReplyJobService.existsReplyInProgress(roomId)).isFalse();
    }

    @Test
    void failedJob_restartJob_requeuesWithNewLockToken() {
        Long userMessageId = createUserMessageAndJob();
        aiReplyJobService.failJob(userMessageId, ERROR_CODE);

        AiReplyJobDispatch dispatch = aiReplyJobService.restartJob(
                roomId, findMessage(userMessageId), USER_ID, NEW_LOCK_TOKEN);

        AiReplyJob job = findJob(userMessageId);
        assertThat(job.getStatus()).isEqualTo(AiReplyJobStatus.QUEUED);
        assertThat(job.getLockToken()).isEqualTo(NEW_LOCK_TOKEN);
        assertThat(job.getFinishedAt()).isNull();
        assertThat(dispatch.dispatchToken()).isEqualTo(job.getDispatchToken());
    }

    @Test
    void abandonedRunningJob_restartJob_closesAsAbandonedAndRequeues() {
        Long userMessageId = takeNewJob().userMessageId();
        markUpdatedLongAgo(userMessageId);

        aiReplyJobService.restartJob(roomId, findMessage(userMessageId), USER_ID, NEW_LOCK_TOKEN);

        AiReplyJob job = findJob(userMessageId);
        assertThat(job.getStatus()).isEqualTo(AiReplyJobStatus.QUEUED);
        assertThat(job.getLastErrorCode()).isEqualTo(AiReplyJobService.ABANDONED_ERROR_CODE);
        assertThat(job.getLockToken()).isEqualTo(NEW_LOCK_TOKEN);
    }

    @Test
    void messageSavedBeforeJobTable_restartJob_createsQueuedJob() {
        AiChatRoom room = aiChatRoomRepository.findById(roomId).orElseThrow();
        AiMessage userMessage = aiMessageRepository.save(
                AiMessage.createUserMessage(room, UUID.randomUUID(), USER_CONTENT));

        AiReplyJobDispatch dispatch = aiReplyJobService.restartJob(roomId, userMessage, USER_ID, NEW_LOCK_TOKEN);

        AiReplyJob job = findJob(userMessage.getId());
        assertThat(dispatch.jobId()).isEqualTo(job.getId());
        assertThat(job.getStatus()).isEqualTo(AiReplyJobStatus.QUEUED);
        assertThat(job.getLockToken()).isEqualTo(NEW_LOCK_TOKEN);
    }

    @Test
    void sameDispatchToken_recordStreamEntry_savesStreamEntryId() {
        AiReplyJobDispatch dispatch = createJob();

        aiReplyJobService.recordStreamEntry(dispatch.jobId(), dispatch.dispatchToken(), STREAM_ENTRY_ID);

        assertThat(findJob(dispatch.userMessage().getId()).getStreamEntryId()).isEqualTo(STREAM_ENTRY_ID);
    }

    @Test
    void dispatchTokenChangedByRestart_recordStreamEntry_keepsJobUnchanged() {
        AiReplyJobDispatch dispatch = createJob();
        aiReplyJobService.failJob(dispatch.userMessage().getId(), ERROR_CODE);
        aiReplyJobService.restartJob(roomId, dispatch.userMessage(), USER_ID, NEW_LOCK_TOKEN);

        aiReplyJobService.recordStreamEntry(dispatch.jobId(), dispatch.dispatchToken(), STREAM_ENTRY_ID);

        assertThat(findJob(dispatch.userMessage().getId()).getStreamEntryId()).isNull();
    }

    @Test
    void currentAttempt_isOwnedBy_returnsTrue() {
        AiReplyJobTakeResult taken = takeNewJob();

        assertThat(aiReplyJobService.isOwnedBy(taken.jobId(), taken.attempt())).isTrue();
    }

    @Test
    void jobTakenByAnotherAttempt_isOwnedBy_returnsFalseForPreviousAttempt() {
        AiReplyJobDispatch dispatch = createJob();
        AiReplyJobTakeResult previous = take(dispatch, 1);
        take(dispatch, 2);

        assertThat(aiReplyJobService.isOwnedBy(previous.jobId(), previous.attempt())).isFalse();
    }

    @Test
    void queuedJob_take_takesFirstAttemptWithUserMessage() {
        AiReplyJobDispatch dispatch = createJob();

        AiReplyJobTakeResult result = aiReplyJobService.take(
                dispatch.jobId(), dispatch.dispatchToken(), 1, STREAM_ENTRY_ID);

        assertThat(result.outcome()).isEqualTo(AiReplyJobTakeResult.Outcome.TAKEN);
        assertThat(result.attempt()).isEqualTo(1);
        assertThat(result.conversationId()).isEqualTo(roomId);
        assertThat(result.lockToken()).isEqualTo(LOCK_TOKEN);
        assertThat(result.userMessage().getContent()).isEqualTo(USER_CONTENT);
        AiReplyJob job = findJob(dispatch.userMessage().getId());
        assertThat(job.getStatus()).isEqualTo(AiReplyJobStatus.RUNNING);
        assertThat(job.getStreamEntryId()).isEqualTo(STREAM_ENTRY_ID);
    }

    @Test
    void runningJobRedelivered_take_raisesAttempt() {
        AiReplyJobDispatch dispatch = createJob();
        take(dispatch, 1);

        AiReplyJobTakeResult result = take(dispatch, 2);

        assertThat(result.outcome()).isEqualTo(AiReplyJobTakeResult.Outcome.TAKEN);
        assertThat(result.attempt()).isEqualTo(2);
    }

    @Test
    void dispatchTokenChangedByRestart_take_returnsStaleWithoutChange() {
        AiReplyJobDispatch dispatch = createJob();
        aiReplyJobService.failJob(dispatch.userMessage().getId(), ERROR_CODE);
        aiReplyJobService.restartJob(roomId, dispatch.userMessage(), USER_ID, NEW_LOCK_TOKEN);

        AiReplyJobTakeResult result = take(dispatch, 1);

        assertThat(result.outcome()).isEqualTo(AiReplyJobTakeResult.Outcome.STALE);
        assertThat(findJob(dispatch.userMessage().getId()).getStatus()).isEqualTo(AiReplyJobStatus.QUEUED);
    }

    @Test
    void failedJob_take_returnsStale() {
        AiReplyJobDispatch dispatch = createJob();
        aiReplyJobService.failJob(dispatch.userMessage().getId(), ERROR_CODE);

        assertThat(take(dispatch, 1).outcome()).isEqualTo(AiReplyJobTakeResult.Outcome.STALE);
    }

    @Test
    void deletedJob_take_returnsStale() {
        assertThat(aiReplyJobService.take(999L, "unknown-token", 1, STREAM_ENTRY_ID).outcome())
                .isEqualTo(AiReplyJobTakeResult.Outcome.STALE);
    }

    @Test
    void succeededJob_take_returnsAlreadySucceededWithoutRaisingAttempt() {
        AiReplyJobDispatch dispatch = createJob();
        AiReplyJobTakeResult taken = take(dispatch, 1);
        commit(taken);

        AiReplyJobTakeResult result = take(dispatch, 2);

        assertThat(result.outcome()).isEqualTo(AiReplyJobTakeResult.Outcome.ALREADY_SUCCEEDED);
        assertThat(result.lockToken()).isEqualTo(LOCK_TOKEN);
        assertThat(findJob(dispatch.userMessage().getId()).getAttempt()).isEqualTo(1);
    }

    @Test
    void deliveredMoreThanMaxDeliveries_take_failsJobAsGaveUp() {
        AiReplyJobDispatch dispatch = createJob();

        AiReplyJobTakeResult result = take(dispatch, MAX_DELIVERIES + 1);

        assertThat(result.outcome()).isEqualTo(AiReplyJobTakeResult.Outcome.GAVE_UP);
        AiReplyJob job = findJob(dispatch.userMessage().getId());
        assertThat(job.getStatus()).isEqualTo(AiReplyJobStatus.FAILED);
        assertThat(job.getLastErrorCode()).isEqualTo(AiReplyJobService.MAX_DELIVERIES_EXCEEDED_ERROR_CODE);
    }

    @Test
    void deliveredMaxDeliveries_take_stillTakes() {
        AiReplyJobDispatch dispatch = createJob();

        assertThat(take(dispatch, MAX_DELIVERIES).outcome()).isEqualTo(AiReplyJobTakeResult.Outcome.TAKEN);
    }

    @Test
    void currentAttempt_commitReply_savesReplyAndSucceedsJob() {
        AiReplyJobTakeResult taken = takeNewJob();

        AiReplyCommitResult result = commit(taken);

        assertThat(result.isRejected()).isFalse();
        assertThat(aiMessageRepository.findByReplyToMessageId(taken.userMessageId()))
                .get().extracting(AiMessage::getId).isEqualTo(result.reply().getId());
        AiReplyJob job = findJob(taken.userMessageId());
        assertThat(job.getStatus()).isEqualTo(AiReplyJobStatus.SUCCEEDED);
        assertThat(job.getFinishedAt()).isNotNull();
    }

    @Test
    void jobTakenByAnotherAttempt_commitReply_rejectsWithoutSavingReply() {
        AiReplyJobDispatch dispatch = createJob();
        AiReplyJobTakeResult previous = take(dispatch, 1);
        take(dispatch, 2);

        AiReplyCommitResult result = commit(previous);

        assertThat(result.isRejected()).isTrue();
        assertThat(aiMessageRepository.findByReplyToMessageId(previous.userMessageId())).isEmpty();
        assertThat(findJob(previous.userMessageId()).getStatus()).isEqualTo(AiReplyJobStatus.RUNNING);
    }

    @Test
    void jobAlreadySucceeded_commitReply_rejectsSecondReply() {
        AiReplyJobTakeResult taken = takeNewJob();
        commit(taken);

        AiReplyCommitResult result = commit(taken);

        assertThat(result.isRejected()).isTrue();
        assertThat(aiMessageRepository.count()).isEqualTo(2);
    }

    @Test
    void replyAlreadySavedOutsideJob_commitReply_throwsAndKeepsJobRunning() {
        AiReplyJobTakeResult taken = takeNewJob();
        aiMessageService.createReplyAndUpdateRoom(roomId, taken.userMessageId(), AI_CONTENT, 20, false, PURGE_AT);

        assertThatThrownBy(() -> commit(taken)).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(findJob(taken.userMessageId()).getStatus()).isEqualTo(AiReplyJobStatus.RUNNING);
    }

    @Test
    void currentAttempt_failTakenJob_recordsErrorCodeAndFinishedAt() {
        AiReplyJobTakeResult taken = takeNewJob();

        boolean recorded = aiReplyJobService.failTakenJob(taken.jobId(), taken.attempt(), ERROR_CODE);

        assertThat(recorded).isTrue();
        AiReplyJob job = findJob(taken.userMessageId());
        assertThat(job.getStatus()).isEqualTo(AiReplyJobStatus.FAILED);
        assertThat(job.getLastErrorCode()).isEqualTo(ERROR_CODE);
        assertThat(job.getFinishedAt()).isNotNull();
    }

    @Test
    void jobTakenByAnotherAttempt_failTakenJob_keepsJobRunning() {
        AiReplyJobDispatch dispatch = createJob();
        AiReplyJobTakeResult previous = take(dispatch, 1);
        take(dispatch, 2);

        boolean recorded = aiReplyJobService.failTakenJob(previous.jobId(), previous.attempt(), ERROR_CODE);

        assertThat(recorded).isFalse();
        assertThat(findJob(previous.userMessageId()).getStatus()).isEqualTo(AiReplyJobStatus.RUNNING);
    }

    @Test
    void queuedJob_failJob_recordsErrorCodeAndFinishedAt() {
        Long userMessageId = createUserMessageAndJob();

        aiReplyJobService.failJob(userMessageId, ERROR_CODE);

        AiReplyJob job = findJob(userMessageId);
        assertThat(job.getStatus()).isEqualTo(AiReplyJobStatus.FAILED);
        assertThat(job.getLastErrorCode()).isEqualTo(ERROR_CODE);
        assertThat(job.getFinishedAt()).isNotNull();
    }

    @Test
    void succeededJob_failJob_keepsSucceeded() {
        AiReplyJobTakeResult taken = takeNewJob();
        commit(taken);

        aiReplyJobService.failJob(taken.userMessageId(), ERROR_CODE);

        AiReplyJob job = findJob(taken.userMessageId());
        assertThat(job.getStatus()).isEqualTo(AiReplyJobStatus.SUCCEEDED);
        assertThat(job.getLastErrorCode()).isNull();
    }

    private Long createUserMessageAndJob() {
        return createJob().userMessage().getId();
    }

    private AiReplyJobDispatch createJob() {
        return aiReplyJobService.createUserMessageAndJob(roomId, USER_ID, UUID.randomUUID(), USER_CONTENT, LOCK_TOKEN);
    }

    private AiReplyJobTakeResult takeNewJob() {
        return take(createJob(), 1);
    }

    private AiReplyJobTakeResult take(AiReplyJobDispatch dispatch, long deliveryCount) {
        return aiReplyJobService.take(dispatch.jobId(), dispatch.dispatchToken(), deliveryCount, STREAM_ENTRY_ID);
    }

    private AiReplyCommitResult commit(AiReplyJobTakeResult taken) {
        return aiReplyJobService.commitReply(taken.jobId(), taken.attempt(), AI_CONTENT, 20, false, PURGE_AT);
    }

    private AiMessage findMessage(Long messageId) {
        return aiMessageRepository.findById(messageId).orElseThrow();
    }

    private AiReplyJob findJob(Long userMessageId) {
        return aiReplyJobRepository.findByUserMessageId(userMessageId).orElseThrow();
    }

    // 처리하던 서버가 죽어 갱신이 멈춘 상황. 엔티티를 거치면 updated_at이 다시 채워지므로 SQL로 직접 바꾼다
    private void markUpdatedLongAgo(Long userMessageId) {
        new JdbcTemplate(dataSource).update(
                "update ai_reply_jobs set updated_at = ? where user_message_id = ?", LONG_AGO, userMessageId);
    }
}
