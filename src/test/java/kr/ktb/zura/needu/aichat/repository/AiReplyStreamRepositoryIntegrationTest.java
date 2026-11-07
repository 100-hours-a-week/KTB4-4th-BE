package kr.ktb.zura.needu.aichat.repository;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import kr.ktb.zura.needu.common.redis.RedisKeyGenerator;
import kr.ktb.zura.needu.common.redis.StreamRedisConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.redis.test.autoconfigure.DataRedisTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;

// Lua와 스트림 명령의 실제 동작(처리 중 목록, 배달 횟수, 대기 시간)은 대역으로 검증할 수 없어 실제 Redis에서 확인한다
@DataRedisTest
@Import({AiReplyStreamRepository.class, RedisKeyGenerator.class, StreamRedisConfig.class})
class AiReplyStreamRepositoryIntegrationTest {

    private static final String KEY = "backend:test:aichat:reply-jobs";
    private static final String WORKER_A = "worker-a";
    private static final String WORKER_B = "worker-b";
    private static final String DISPATCH_TOKEN = "dispatch-token-1";
    private static final Duration SHORT_MIN_IDLE = Duration.ofMillis(100);
    private static final Duration LONG_MIN_IDLE = Duration.ofSeconds(30);

    @Autowired
    private AiReplyStreamRepository repository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        redisTemplate.delete(KEY);
        repository.createGroupIfAbsent();
    }

    @AfterEach
    void tearDown() {
        redisTemplate.delete(KEY);
    }

    @Test
    void addedJob_readNew_returnsEntryOnceWithFirstDelivery() {
        String entryId = repository.add(42L, DISPATCH_TOKEN);

        Optional<AiReplyStreamEntry> entry = repository.readNew(WORKER_A);

        assertThat(entry).contains(new AiReplyStreamEntry(entryId, 42L, DISPATCH_TOKEN, 1));
        assertThat(repository.readNew(WORKER_B)).isEmpty();
        assertThat(repository.isPending(entryId)).isTrue();
        assertThat(repository.size()).isEqualTo(1);
    }

    @Test
    void groupAlreadyExists_createGroupIfAbsent_doesNotThrow() {
        repository.createGroupIfAbsent();
    }

    // Redis가 데이터를 잃은 뒤 그룹보다 먼저 다시 넣은 작업도 배달돼야 한다
    @Test
    void jobAddedBeforeGroup_createGroupIfAbsent_deliversJob() {
        redisTemplate.delete(KEY);
        String entryId = repository.add(42L, DISPATCH_TOKEN);

        repository.createGroupIfAbsent();

        assertThat(repository.readNew(WORKER_A)).map(AiReplyStreamEntry::entryId).contains(entryId);
    }

    @Test
    void groupLostWithRedisData_readNew_recreatesGroupAndReturnsEmpty() {
        redisTemplate.delete(KEY);

        assertThat(repository.readNew(WORKER_A)).isEmpty();

        String entryId = repository.add(42L, DISPATCH_TOKEN);
        assertThat(repository.readNew(WORKER_A)).map(AiReplyStreamEntry::entryId).contains(entryId);
    }

    @Test
    void ownerHeartbeat_heartbeat_resetsIdleWithoutIncreasingDeliveryCount() throws InterruptedException {
        String entryId = readNewEntry(WORKER_A);
        Thread.sleep(200);

        AiReplyHeartbeatResult result = repository.heartbeat(WORKER_A, entryId);

        assertThat(result).isEqualTo(AiReplyHeartbeatResult.OWNED);
        PendingMessage pending = findPending(entryId);
        assertThat(pending.getElapsedTimeSinceLastDelivery()).isLessThan(Duration.ofMillis(200));
        assertThat(pending.getTotalDeliveryCount()).isEqualTo(1);
    }

    @Test
    void entryNotIdleLongEnough_claimIdle_claimsNothing() {
        String entryId = readNewEntry(WORKER_A);

        AiReplyStreamClaimResult result = repository.claimIdle(WORKER_B, LONG_MIN_IDLE);

        assertThat(result.hasClaimed()).isFalse();
        assertThat(findPending(entryId).getConsumerName()).isEqualTo(WORKER_A);
    }

    @Test
    void entryIdlePastMinIdle_claimIdle_changesOwnerAndIncreasesDeliveryCount() throws InterruptedException {
        String entryId = readNewEntry(WORKER_A);
        Thread.sleep(200);

        AiReplyStreamClaimResult result = repository.claimIdle(WORKER_B, SHORT_MIN_IDLE);

        assertThat(result.claimed()).isEqualTo(new AiReplyStreamEntry(entryId, 42L, DISPATCH_TOKEN, 2));
        PendingMessage pending = findPending(entryId);
        assertThat(pending.getConsumerName()).isEqualTo(WORKER_B);
        assertThat(pending.getTotalDeliveryCount()).isEqualTo(2);
    }

    // XAUTOCLAIM은 한 번에 처리 중 목록 일부만 살핀다. 앞쪽이 모두 처리 중이어도 뒤의 오래된 항목을 찾아야 한다
    @Test
    void idleEntryBehindManyActiveEntries_claimIdle_findsIdleEntry() throws InterruptedException {
        for (long jobId = 100; jobId < 130; jobId++) {
            repository.add(jobId, DISPATCH_TOKEN);
            repository.readNew(WORKER_A);
        }
        String idleEntryId = readNewEntry(WORKER_A);
        Thread.sleep(200);
        heartbeatAllExcept(idleEntryId);

        AiReplyStreamClaimResult result = repository.claimIdle(WORKER_B, SHORT_MIN_IDLE);

        assertThat(result.claimed().entryId()).isEqualTo(idleEntryId);
    }

    @Test
    void claimedByOtherWorker_heartbeat_returnsResultPerOwner() throws InterruptedException {
        String entryId = readNewEntry(WORKER_A);
        Thread.sleep(200);
        repository.claimIdle(WORKER_B, SHORT_MIN_IDLE);

        assertThat(repository.heartbeat(WORKER_A, entryId)).isEqualTo(AiReplyHeartbeatResult.CLAIMED_BY_OTHER);
        assertThat(repository.heartbeat(WORKER_B, entryId)).isEqualTo(AiReplyHeartbeatResult.OWNED);
        assertThat(findPending(entryId).getConsumerName()).isEqualTo(WORKER_B);
    }

    @Test
    void entryNotPending_heartbeat_returnsNotPending() {
        String entryId = readNewEntry(WORKER_A);
        repository.acknowledgeAndDelete(entryId);

        assertThat(repository.heartbeat(WORKER_A, entryId)).isEqualTo(AiReplyHeartbeatResult.NOT_PENDING);
    }

    @Test
    void pendingEntryDeletedFromStream_claimIdle_returnsDeletedIdAndClearsPending() throws InterruptedException {
        String entryId = readNewEntry(WORKER_A);
        redisTemplate.opsForStream().delete(KEY, entryId);
        Thread.sleep(200);

        AiReplyStreamClaimResult result = repository.claimIdle(WORKER_B, SHORT_MIN_IDLE);

        assertThat(result.hasClaimed()).isFalse();
        assertThat(result.deletedEntryIds()).containsExactly(entryId);
        assertThat(repository.isPending(entryId)).isFalse();
    }

    @Test
    void processedEntry_acknowledgeAndDelete_removesFromPendingAndStream() {
        String entryId = readNewEntry(WORKER_A);

        repository.acknowledgeAndDelete(entryId);

        assertThat(repository.isPending(entryId)).isFalse();
        assertThat(repository.exists(entryId)).isFalse();
        assertThat(repository.size()).isZero();
    }

    @Test
    void waitingEntry_existsAndIsPending_returnsExistsButNotPending() {
        String entryId = repository.add(42L, DISPATCH_TOKEN);

        assertThat(repository.exists(entryId)).isTrue();
        assertThat(repository.isPending(entryId)).isFalse();
    }

    @Test
    void entryWithoutJobFields_readNew_returnsMalformedEntry() {
        redisTemplate.opsForStream().add(KEY, Map.of("unexpected", "value"));

        Optional<AiReplyStreamEntry> entry = repository.readNew(WORKER_A);

        assertThat(entry).get().extracting(AiReplyStreamEntry::isMalformed).isEqualTo(true);
    }

    // Redis 재시작·장애 조치로 스크립트 캐시가 비어도 DefaultRedisScript가 EVAL로 다시 실행해야 한다
    @Test
    void scriptCacheFlushed_heartbeat_stillRunsScript() {
        String entryId = readNewEntry(WORKER_A);
        repository.heartbeat(WORKER_A, entryId);
        redisTemplate.execute((RedisCallback<Void>) connection -> {
            flushScripts(connection);
            return null;
        });

        assertThat(repository.heartbeat(WORKER_A, entryId)).isEqualTo(AiReplyHeartbeatResult.OWNED);
    }

    private String readNewEntry(String consumerName) {
        repository.add(42L, DISPATCH_TOKEN);
        return repository.readNew(consumerName).orElseThrow().entryId();
    }

    private PendingMessage findPending(String entryId) {
        PendingMessages pending = redisTemplate.opsForStream()
                .pending(KEY, AiReplyStreamRepository.GROUP, Range.closed(entryId, entryId), 1);
        return pending.get(0);
    }

    private void heartbeatAllExcept(String entryId) {
        List<PendingMessage> pending = redisTemplate.opsForStream()
                .pending(KEY, AiReplyStreamRepository.GROUP, Range.unbounded(), 100).toList();
        pending.stream()
                .map(PendingMessage::getIdAsString)
                .filter(id -> !id.equals(entryId))
                .forEach(id -> repository.heartbeat(WORKER_A, id));
    }

    private void flushScripts(RedisConnection connection) {
        connection.scriptingCommands().scriptFlush();
    }
}
