package kr.ktb.zura.needu.aichat.repository;

import java.util.List;
import java.util.stream.IntStream;

import kr.ktb.zura.needu.aichat.config.AiReplyQueueConfig;
import kr.ktb.zura.needu.common.redis.RedisKeyGenerator;
import kr.ktb.zura.needu.common.redis.StreamRedisConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.redis.test.autoconfigure.DataRedisTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@DataRedisTest(properties = {
        "ai.chat.reply-queue.global-max-concurrency=3",
        "ai.chat.reply-queue.permit-ttl=200ms"
})
@Import({AiReplyPermitRepository.class, AiReplyQueueConfig.class, RedisKeyGenerator.class, StreamRedisConfig.class})
class AiReplyPermitRepositoryIntegrationTest {

    private static final String KEY = "backend:test:aichat:reply-permits";

    @Autowired
    private AiReplyPermitRepository repository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        redisTemplate.delete(KEY);
    }

    @AfterEach
    void tearDown() {
        redisTemplate.delete(KEY);
    }

    @Test
    void limitThree_acquireFiveTimes_grantsOnlyThree() {
        List<Boolean> results = IntStream.range(0, 5)
                .mapToObj(i -> repository.acquire("permit-" + i))
                .toList();

        assertThat(results).containsExactly(true, true, true, false, false);
    }

    @Test
    void permitsExpired_acquire_grantsAgainAndClearsExpired() throws InterruptedException {
        IntStream.range(0, 3).forEach(i -> repository.acquire("permit-" + i));
        Thread.sleep(300);

        assertThat(repository.acquire("permit-new")).isTrue();
        assertThat(redisTemplate.opsForZSet().size(KEY)).isEqualTo(1);
    }

    @Test
    void releasedPermit_acquire_grantsFreedSlot() {
        IntStream.range(0, 3).forEach(i -> repository.acquire("permit-" + i));

        repository.release("permit-0");

        assertThat(repository.acquire("permit-new")).isTrue();
    }

    @Test
    void heldPermit_extend_returnsTrueAndPushesExpiry() throws InterruptedException {
        repository.acquire("permit-0");
        Double before = redisTemplate.opsForZSet().score(KEY, "permit-0");
        Thread.sleep(50);

        boolean held = repository.extend("permit-0");

        assertThat(held).isTrue();
        assertThat(redisTemplate.opsForZSet().score(KEY, "permit-0")).isGreaterThan(before);
    }

    @Test
    void permitAlreadyCleared_extend_returnsFalseAndAddsBack() {
        boolean held = repository.extend("permit-0");

        assertThat(held).isFalse();
        assertThat(redisTemplate.opsForZSet().score(KEY, "permit-0")).isNotNull();
    }

    @Test
    void permitsHeldAndReleased_countInUse_countsOnlyHeldPermits() {
        IntStream.range(0, 3).forEach(i -> repository.acquire("permit-" + i));

        repository.release("permit-0");

        assertThat(repository.countInUse()).isEqualTo(2);
    }

    // 만료된 허가는 다음 획득 전까지 남아 있다 => 사용 중으로 세지 않아야 한다
    @Test
    void permitsExpiredButNotCleared_countInUse_returnsZero() throws InterruptedException {
        IntStream.range(0, 2).forEach(i -> repository.acquire("permit-" + i));
        Thread.sleep(300);

        assertThat(repository.countInUse()).isZero();
        assertThat(redisTemplate.opsForZSet().size(KEY)).isEqualTo(2);
    }
}
