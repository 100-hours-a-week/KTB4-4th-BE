package kr.ktb.zura.needu.aichat.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import javax.sql.DataSource;

import kr.ktb.zura.needu.aichat.config.AiConversationLockProperties;
import kr.ktb.zura.needu.aichat.config.AiReplyQueueProperties;
import kr.ktb.zura.needu.aichat.entity.AiChatRoom;
import kr.ktb.zura.needu.aichat.entity.AiReplyJob;
import kr.ktb.zura.needu.aichat.repository.AiChatRoomRepository;
import kr.ktb.zura.needu.aichat.repository.AiMessageRepository;
import kr.ktb.zura.needu.aichat.repository.AiReplyJobRepository;
import kr.ktb.zura.needu.aichat.repository.AiReplyPermitRepository;
import kr.ktb.zura.needu.aichat.repository.AiReplyStreamEntry;
import kr.ktb.zura.needu.aichat.repository.AiReplyStreamRepository;
import kr.ktb.zura.needu.aichat.type.AiReplyJobStatus;
import kr.ktb.zura.needu.common.redis.RedisKeyGenerator;
import kr.ktb.zura.needu.common.redis.StreamRedisConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

// 스트림 항목 확인(XRANGE·XPENDING)과 DB 조건 확인이 함께 맞아야 해서 실제 MySQL과 Redis에서 확인한다
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@ImportAutoConfiguration(DataRedisAutoConfiguration.class)
@Import({AiReplyQueueService.class, AiReplyJobService.class, AiMessageService.class,
        AiReplyStreamRepository.class, AiReplyPermitRepository.class, RedisKeyGenerator.class,
        StreamRedisConfig.class, AiReplyJobReconcileIntegrationTest.Config.class})
class AiReplyJobReconcileIntegrationTest {

    private static final String STREAM_KEY = "backend:test:aichat:reply-jobs";
    private static final Long USER_ID = 1L;
    private static final String LOCK_TOKEN = "lock-token-1";
    private static final String USER_CONTENT = "요즘 러닝에 관심이 생겼어.";
    private static final String WORKER_A = "worker-a";
    private static final String WORKER_B = "worker-b";
    private static final LocalDateTime LONG_AGO = LocalDateTime.of(2026, 1, 1, 0, 0);
    private static final Duration STALE_AFTER = Duration.ofSeconds(15);

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
            return new AiReplyQueueProperties(true, 96, Duration.ofSeconds(30), 3,
                    Duration.ofSeconds(5), Duration.ofSeconds(30), Duration.ofSeconds(5), Duration.ofMillis(200),
                    Duration.ofSeconds(2));
        }
    }

    @Autowired
    private AiReplyQueueService aiReplyQueueService;

    @Autowired
    private AiReplyJobService aiReplyJobService;

    @Autowired
    private AiReplyStreamRepository aiReplyStreamRepository;

    @Autowired
    private AiReplyJobRepository aiReplyJobRepository;

    @Autowired
    private AiMessageRepository aiMessageRepository;

    @Autowired
    private AiChatRoomRepository aiChatRoomRepository;

    @Autowired
    @Qualifier(StreamRedisConfig.STREAM_REDIS_TEMPLATE)
    private StringRedisTemplate streamRedisTemplate;

    @Autowired
    private DataSource dataSource;

    private Long roomId;

    @BeforeEach
    void setUp() {
        streamRedisTemplate.delete(STREAM_KEY);
        aiReplyStreamRepository.createGroupIfAbsent();
        roomId = aiChatRoomRepository.save(AiChatRoom.reserve(USER_ID)).getId();
    }

    @AfterEach
    void tearDown() {
        streamRedisTemplate.delete(STREAM_KEY);
        aiReplyJobRepository.deleteAllInBatch();
        aiMessageRepository.deleteAllInBatch();
        aiChatRoomRepository.deleteAllInBatch();
    }

    // S5: XADD는 됐지만 결과(스트림 항목 ID)를 DB에 남기지 못한 작업
    @Test
    void streamEntryNotRecorded_redispatchLostJobs_addsNewEntryAndOldEntryBecomesStale() {
        AiReplyJobDispatch dispatch = createJob();
        String oldEntryId = aiReplyStreamRepository.add(dispatch.jobId(), dispatch.dispatchToken());
        markUpdatedLongAgo(dispatch.jobId());

        aiReplyQueueService.redispatchLostJobs(STALE_AFTER);

        AiReplyJob job = findJob(dispatch.jobId());
        assertThat(job.getStatus()).isEqualTo(AiReplyJobStatus.QUEUED);
        assertThat(job.getDispatchToken()).isNotEqualTo(dispatch.dispatchToken());
        assertThat(job.getStreamEntryId()).isNotNull().isNotEqualTo(oldEntryId);
        assertThat(aiReplyStreamRepository.exists(job.getStreamEntryId())).isTrue();

        AiReplyJobTakeResult oldTake = aiReplyJobService.take(
                dispatch.jobId(), dispatch.dispatchToken(), 1, oldEntryId);
        AiReplyJobTakeResult newTake = aiReplyJobService.take(
                dispatch.jobId(), job.getDispatchToken(), 1, job.getStreamEntryId());
        assertThat(oldTake.outcome()).isEqualTo(AiReplyJobTakeResult.Outcome.STALE);
        assertThat(newTake.outcome()).isEqualTo(AiReplyJobTakeResult.Outcome.TAKEN);
        assertThat(newTake.attempt()).isEqualTo(1);
    }

    // S6: 처리 중에 Redis가 스트림을 잃음. 다시 넣은 작업은 다음 워커가 attempt 2로 가져간다
    @Test
    void streamLostWhileRunning_redispatchLostJobs_nextWorkerTakesSecondAttempt() {
        AiReplyJobDispatch dispatch = createJob();
        aiReplyQueueService.enqueue(dispatch.jobId(), dispatch.dispatchToken());
        AiReplyStreamEntry entry = aiReplyStreamRepository.readNew(WORKER_A).orElseThrow();
        aiReplyJobService.take(dispatch.jobId(), dispatch.dispatchToken(), 1, entry.entryId());
        streamRedisTemplate.delete(STREAM_KEY);
        markUpdatedLongAgo(dispatch.jobId());

        aiReplyQueueService.redispatchLostJobs(STALE_AFTER);

        AiReplyJob job = findJob(dispatch.jobId());
        assertThat(job.getStatus()).isEqualTo(AiReplyJobStatus.QUEUED);
        assertThat(job.getAttempt()).isEqualTo(1);
        AiReplyStreamEntry redispatched = readNewAfterGroupRecreated();
        assertThat(redispatched.jobId()).isEqualTo(dispatch.jobId());
        assertThat(redispatched.dispatchToken()).isEqualTo(job.getDispatchToken());
        AiReplyJobTakeResult taken = aiReplyJobService.take(
                redispatched.jobId(), redispatched.dispatchToken(), redispatched.deliveryCount(),
                redispatched.entryId());
        assertThat(taken.outcome()).isEqualTo(AiReplyJobTakeResult.Outcome.TAKEN);
        assertThat(taken.attempt()).isEqualTo(2);
    }

    @Test
    void jobWaitingInStream_redispatchLostJobs_keepsJob() {
        AiReplyJobDispatch dispatch = createJob();
        aiReplyQueueService.enqueue(dispatch.jobId(), dispatch.dispatchToken());
        markUpdatedLongAgo(dispatch.jobId());

        aiReplyQueueService.redispatchLostJobs(STALE_AFTER);

        AiReplyJob job = findJob(dispatch.jobId());
        assertThat(job.getDispatchToken()).isEqualTo(dispatch.dispatchToken());
        assertThat(aiReplyStreamRepository.size()).isEqualTo(1);
    }

    @Test
    void jobRunningWithPendingEntry_redispatchLostJobs_keepsJob() {
        AiReplyJobDispatch dispatch = createJob();
        aiReplyQueueService.enqueue(dispatch.jobId(), dispatch.dispatchToken());
        AiReplyStreamEntry entry = aiReplyStreamRepository.readNew(WORKER_A).orElseThrow();
        aiReplyJobService.take(dispatch.jobId(), dispatch.dispatchToken(), 1, entry.entryId());
        markUpdatedLongAgo(dispatch.jobId());

        aiReplyQueueService.redispatchLostJobs(STALE_AFTER);

        AiReplyJob job = findJob(dispatch.jobId());
        assertThat(job.getStatus()).isEqualTo(AiReplyJobStatus.RUNNING);
        assertThat(job.getDispatchToken()).isEqualTo(dispatch.dispatchToken());
        assertThat(aiReplyStreamRepository.size()).isEqualTo(1);
    }

    private AiReplyJobDispatch createJob() {
        return aiReplyJobService.createUserMessageAndJob(roomId, USER_ID, UUID.randomUUID(), USER_CONTENT, LOCK_TOKEN);
    }

    private AiReplyJob findJob(Long jobId) {
        return aiReplyJobRepository.findById(jobId).orElseThrow();
    }

    // 스트림과 함께 그룹도 사라졌으므로 첫 읽기는 그룹만 다시 만들고 빈 결과를 돌려준다
    private AiReplyStreamEntry readNewAfterGroupRecreated() {
        assertThat(aiReplyStreamRepository.readNew(WORKER_B)).isEmpty();
        return aiReplyStreamRepository.readNew(WORKER_B).orElseThrow();
    }

    // 엔티티를 거치면 updated_at이 다시 채워지므로 SQL로 직접 바꾼다
    private void markUpdatedLongAgo(Long jobId) {
        new JdbcTemplate(dataSource).update("update ai_reply_jobs set updated_at = ? where id = ?", LONG_AGO, jobId);
    }
}
