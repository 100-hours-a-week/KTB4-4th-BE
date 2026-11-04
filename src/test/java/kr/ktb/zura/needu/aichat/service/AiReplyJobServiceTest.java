package kr.ktb.zura.needu.aichat.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import javax.sql.DataSource;

import kr.ktb.zura.needu.aichat.config.AiConversationLockProperties;
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
    }

    @Autowired
    private AiReplyJobService aiReplyJobService;

    @Autowired
    private AiReplyJobRepository aiReplyJobRepository;

    @Autowired
    private AiMessageRepository aiMessageRepository;

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

        AiMessage userMessage = aiReplyJobService.createUserMessageAndJob(
                roomId, USER_ID, clientMessageId, USER_CONTENT, LOCK_TOKEN);

        assertThat(aiMessageRepository.findByAiChatRoomIdAndClientMessageId(roomId, clientMessageId)).isPresent();
        AiReplyJob job = findJob(userMessage.getId());
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
        Long userMessageId = createUserMessageAndJob();
        aiReplyJobService.startJob(userMessageId);
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
        Long userMessageId = createUserMessageAndJob();
        aiReplyJobService.startJob(userMessageId);
        markUpdatedLongAgo(userMessageId);

        assertThat(aiReplyJobService.existsReplyInProgress(roomId)).isFalse();
    }

    @Test
    void failedJob_restartJob_requeuesWithNewLockToken() {
        Long userMessageId = createUserMessageAndJob();
        aiReplyJobService.failJob(userMessageId, ERROR_CODE);

        aiReplyJobService.restartJob(roomId, userMessageId, USER_ID, NEW_LOCK_TOKEN);

        AiReplyJob job = findJob(userMessageId);
        assertThat(job.getStatus()).isEqualTo(AiReplyJobStatus.QUEUED);
        assertThat(job.getLockToken()).isEqualTo(NEW_LOCK_TOKEN);
        assertThat(job.getFinishedAt()).isNull();
    }

    @Test
    void abandonedRunningJob_restartJob_closesAsAbandonedAndRequeues() {
        Long userMessageId = createUserMessageAndJob();
        aiReplyJobService.startJob(userMessageId);
        markUpdatedLongAgo(userMessageId);

        aiReplyJobService.restartJob(roomId, userMessageId, USER_ID, NEW_LOCK_TOKEN);

        AiReplyJob job = findJob(userMessageId);
        assertThat(job.getStatus()).isEqualTo(AiReplyJobStatus.QUEUED);
        assertThat(job.getLastErrorCode()).isEqualTo(AiReplyJobService.ABANDONED_ERROR_CODE);
        assertThat(job.getLockToken()).isEqualTo(NEW_LOCK_TOKEN);
    }

    @Test
    void messageSavedBeforeJobTable_restartJob_createsQueuedJob() {
        AiChatRoom room = aiChatRoomRepository.findById(roomId).orElseThrow();
        Long userMessageId = aiMessageRepository.save(
                AiMessage.createUserMessage(room, UUID.randomUUID(), USER_CONTENT)).getId();

        aiReplyJobService.restartJob(roomId, userMessageId, USER_ID, NEW_LOCK_TOKEN);

        AiReplyJob job = findJob(userMessageId);
        assertThat(job.getStatus()).isEqualTo(AiReplyJobStatus.QUEUED);
        assertThat(job.getLockToken()).isEqualTo(NEW_LOCK_TOKEN);
    }

    @Test
    void queuedJob_startJob_becomesRunning() {
        Long userMessageId = createUserMessageAndJob();

        aiReplyJobService.startJob(userMessageId);

        assertThat(findJob(userMessageId).getStatus()).isEqualTo(AiReplyJobStatus.RUNNING);
    }

    @Test
    void runningJob_createReplyAndSucceed_savesReplyAndSucceedsJob() {
        Long userMessageId = createUserMessageAndJob();
        aiReplyJobService.startJob(userMessageId);

        AiMessage reply = aiReplyJobService.createReplyAndSucceed(
                roomId, userMessageId, AI_CONTENT, 20, false, PURGE_AT);

        assertThat(aiMessageRepository.findByReplyToMessageId(userMessageId))
                .get().extracting(AiMessage::getId).isEqualTo(reply.getId());
        AiReplyJob job = findJob(userMessageId);
        assertThat(job.getStatus()).isEqualTo(AiReplyJobStatus.SUCCEEDED);
        assertThat(job.getFinishedAt()).isNotNull();
    }

    @Test
    void jobNotRunning_createReplyAndSucceed_throwsAndRollsBackReply() {
        Long userMessageId = createUserMessageAndJob();

        assertThatThrownBy(() -> aiReplyJobService.createReplyAndSucceed(
                roomId, userMessageId, AI_CONTENT, 20, false, PURGE_AT))
                .isInstanceOf(IllegalStateException.class);

        assertThat(aiMessageRepository.findByReplyToMessageId(userMessageId)).isEmpty();
        assertThat(findJob(userMessageId).getStatus()).isEqualTo(AiReplyJobStatus.QUEUED);
    }

    @Test
    void runningJob_failJob_recordsErrorCodeAndFinishedAt() {
        Long userMessageId = createUserMessageAndJob();
        aiReplyJobService.startJob(userMessageId);

        aiReplyJobService.failJob(userMessageId, ERROR_CODE);

        AiReplyJob job = findJob(userMessageId);
        assertThat(job.getStatus()).isEqualTo(AiReplyJobStatus.FAILED);
        assertThat(job.getLastErrorCode()).isEqualTo(ERROR_CODE);
        assertThat(job.getFinishedAt()).isNotNull();
    }

    @Test
    void succeededJob_failJob_keepsSucceeded() {
        Long userMessageId = createUserMessageAndJob();
        aiReplyJobService.startJob(userMessageId);
        aiReplyJobService.createReplyAndSucceed(roomId, userMessageId, AI_CONTENT, 20, false, PURGE_AT);

        aiReplyJobService.failJob(userMessageId, ERROR_CODE);

        AiReplyJob job = findJob(userMessageId);
        assertThat(job.getStatus()).isEqualTo(AiReplyJobStatus.SUCCEEDED);
        assertThat(job.getLastErrorCode()).isNull();
    }

    private Long createUserMessageAndJob() {
        return aiReplyJobService.createUserMessageAndJob(
                roomId, USER_ID, UUID.randomUUID(), USER_CONTENT, LOCK_TOKEN).getId();
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
