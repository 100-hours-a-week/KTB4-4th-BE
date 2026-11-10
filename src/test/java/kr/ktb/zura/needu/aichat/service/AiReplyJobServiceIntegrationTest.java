package kr.ktb.zura.needu.aichat.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import kr.ktb.zura.needu.aichat.config.AiConversationLockProperties;
import kr.ktb.zura.needu.aichat.config.AiReplyQueueProperties;
import kr.ktb.zura.needu.aichat.entity.AiChatRoom;
import kr.ktb.zura.needu.aichat.entity.AiMessage;
import kr.ktb.zura.needu.aichat.repository.AiChatRoomRepository;
import kr.ktb.zura.needu.aichat.repository.AiMessageRepository;
import kr.ktb.zura.needu.aichat.repository.AiReplyJobRepository;
import kr.ktb.zura.needu.aichat.type.AiReplyJobStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 행 잠금(FOR UPDATE) 대기 순서는 H2와 다를 수 있어 integration 프로필의 MySQL에서 확인한다
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({AiReplyJobService.class, AiMessageService.class, AiReplyJobServiceIntegrationTest.Config.class})
class AiReplyJobServiceIntegrationTest {

    private static final Long USER_ID = 1L;
    private static final String LOCK_TOKEN = "lock-token-1";
    private static final String USER_CONTENT = "요즘 러닝에 관심이 생겼어.";
    private static final String OLDER_REPLY = "러닝을 좋아하시는군요.";
    private static final String NEWER_REPLY = "러닝을 즐기시는군요.";
    private static final LocalDateTime PURGE_AT = LocalDateTime.of(2026, 11, 7, 12, 30);
    private static final long WAIT_SECONDS = 5;
    // 이 시간 안에 끝나지 않으면 행 잠금을 기다리는 중으로 본다
    private static final long BLOCKED_CHECK_MILLIS = 300;

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
            return new AiReplyQueueProperties(false, 96, Duration.ofSeconds(30), 3,
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
    private AiChatRoomRepository aiChatRoomRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private Long roomId;

    @BeforeEach
    void setUp() {
        roomId = aiChatRoomRepository.save(AiChatRoom.reserve(USER_ID)).getId();
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
        aiReplyJobRepository.deleteAllInBatch();
        aiMessageRepository.deleteAllInBatch();
        aiChatRoomRepository.deleteAllInBatch();
    }

    // S3a: 회수한 처리(2번)가 저장 중일 때 멈췄던 처리(1번)가 깨어나 저장을 시도
    @Test
    void olderAttemptCommitsWhileNewerAttemptHoldsRow_commitReply_savesOnlyNewerReply() throws Exception {
        AiReplyJobDispatch dispatch = createJob();
        AiReplyJobTakeResult older = take(dispatch, 1);
        AiReplyJobTakeResult newer = take(dispatch, 2);
        CountDownLatch newerSaved = new CountDownLatch(1);
        CountDownLatch newerMayCommit = new CountDownLatch(1);

        Future<AiReplyCommitResult> newerResult = executor.submit(() -> inTransaction(() -> {
            AiReplyCommitResult result = commit(newer, NEWER_REPLY);
            newerSaved.countDown();
            await(newerMayCommit);
            return result;
        }));
        assertThat(newerSaved.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
        Future<AiReplyCommitResult> olderResult = executor.submit(() -> commit(older, OLDER_REPLY));

        assertThatThrownBy(() -> olderResult.get(BLOCKED_CHECK_MILLIS, TimeUnit.MILLISECONDS))
                .isInstanceOf(TimeoutException.class);
        newerMayCommit.countDown();

        assertThat(newerResult.get(WAIT_SECONDS, TimeUnit.SECONDS).isRejected()).isFalse();
        assertThat(olderResult.get(WAIT_SECONDS, TimeUnit.SECONDS).isRejected()).isTrue();
        assertOnlyReply(dispatch, NEWER_REPLY);
    }

    // S3b: 회수한 처리(2번)가 저장을 끝낸 뒤 멈췄던 처리(1번)가 깨어나 저장을 시도
    @Test
    void olderAttemptCommitsAfterNewerAttemptCommitted_commitReply_rejectsOlderReply() {
        AiReplyJobDispatch dispatch = createJob();
        AiReplyJobTakeResult older = take(dispatch, 1);
        AiReplyJobTakeResult newer = take(dispatch, 2);

        AiReplyCommitResult newerResult = commit(newer, NEWER_REPLY);
        AiReplyCommitResult olderResult = commit(older, OLDER_REPLY);

        assertThat(newerResult.isRejected()).isFalse();
        assertThat(olderResult.isRejected()).isTrue();
        assertOnlyReply(dispatch, NEWER_REPLY);
    }

    // 회수가 처리 번호를 올리기 전에 이전 처리가 저장을 시작하면, 회수는 저장이 끝난 걸 보고 다시 만들지 않는다
    @Test
    void redeliveredWhileCurrentAttemptHoldsRow_take_waitsAndReturnsAlreadySucceeded() throws Exception {
        AiReplyJobDispatch dispatch = createJob();
        AiReplyJobTakeResult current = take(dispatch, 1);
        CountDownLatch currentSaved = new CountDownLatch(1);
        CountDownLatch currentMayCommit = new CountDownLatch(1);

        Future<AiReplyCommitResult> currentResult = executor.submit(() -> inTransaction(() -> {
            AiReplyCommitResult result = commit(current, OLDER_REPLY);
            currentSaved.countDown();
            await(currentMayCommit);
            return result;
        }));
        assertThat(currentSaved.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
        Future<AiReplyJobTakeResult> redelivered = executor.submit(() -> take(dispatch, 2));

        assertThatThrownBy(() -> redelivered.get(BLOCKED_CHECK_MILLIS, TimeUnit.MILLISECONDS))
                .isInstanceOf(TimeoutException.class);
        currentMayCommit.countDown();

        assertThat(currentResult.get(WAIT_SECONDS, TimeUnit.SECONDS).isRejected()).isFalse();
        assertThat(redelivered.get(WAIT_SECONDS, TimeUnit.SECONDS).outcome())
                .isEqualTo(AiReplyJobTakeResult.Outcome.ALREADY_SUCCEEDED);
        assertThat(aiReplyJobRepository.findById(dispatch.jobId()).orElseThrow().getAttempt()).isEqualTo(1);
        assertOnlyReply(dispatch, OLDER_REPLY);
    }

    private AiReplyJobDispatch createJob() {
        return aiReplyJobService.createUserMessageAndJob(roomId, USER_ID, UUID.randomUUID(), USER_CONTENT, LOCK_TOKEN);
    }

    private AiReplyJobTakeResult take(AiReplyJobDispatch dispatch, long deliveryCount) {
        return aiReplyJobService.take(dispatch.jobId(), dispatch.dispatchToken(), deliveryCount, null);
    }

    private AiReplyCommitResult commit(AiReplyJobTakeResult taken, String content) {
        return aiReplyJobService.commitReply(taken.jobId(), taken.attempt(), content, 20, false, PURGE_AT);
    }

    // commitReply가 바깥 트랜잭션에 참여해 행 잠금을 쥔 채 커밋 전에 멈추게 한다
    private <T> T inTransaction(Supplier<T> action) {
        return new TransactionTemplate(transactionManager).execute(status -> action.get());
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("latch timed out");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private void assertOnlyReply(AiReplyJobDispatch dispatch, String content) {
        Long userMessageId = dispatch.userMessage().getId();
        assertThat(aiMessageRepository.findByReplyToMessageId(userMessageId))
                .get().extracting(AiMessage::getContent).isEqualTo(content);
        assertThat(aiMessageRepository.count()).isEqualTo(2);
        assertThat(aiReplyJobRepository.findById(dispatch.jobId()).orElseThrow().getStatus())
                .isEqualTo(AiReplyJobStatus.SUCCEEDED);
    }
}
