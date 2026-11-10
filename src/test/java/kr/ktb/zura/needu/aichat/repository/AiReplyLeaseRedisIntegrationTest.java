package kr.ktb.zura.needu.aichat.repository;

import java.util.List;
import java.util.concurrent.TimeUnit;

import kr.ktb.zura.needu.aichat.config.AiConversationLockConfig;
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

// 워커가 답변을 만드는 동안 대화방 락과 생성 중 표시를 유지하는 Lua를 실제 Redis에서 확인한다
@DataRedisTest
@Import({AiConversationLockRepository.class, AiGenerationStateRepository.class,
        AiConversationLockConfig.class, RedisKeyGenerator.class, StreamRedisConfig.class})
class AiReplyLeaseRedisIntegrationTest {

    private static final Long CONVERSATION_ID = 101L;
    private static final String LOCK_KEY = "backend:test:aichat:lock:101";
    private static final String GENERATING_KEY = "backend:test:aichat:generating:101";
    private static final String OWNER_TOKEN = "owner-token";
    private static final String OTHER_TOKEN = "other-token";
    private static final long SHORT_TTL_MILLIS = 1_000;
    private static final long CONFIGURED_TTL_MILLIS = 120_000;

    @Autowired
    private AiConversationLockRepository lockRepository;

    @Autowired
    private AiGenerationStateRepository generationStateRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        redisTemplate.delete(List.of(LOCK_KEY, GENERATING_KEY));
    }

    @AfterEach
    void tearDown() {
        redisTemplate.delete(List.of(LOCK_KEY, GENERATING_KEY));
    }

    @Test
    void ownLock_ensure_extendsTtl() {
        redisTemplate.opsForValue().set(LOCK_KEY, OWNER_TOKEN, SHORT_TTL_MILLIS, TimeUnit.MILLISECONDS);

        AiConversationLockRepository.EnsureResult result = lockRepository.ensure(CONVERSATION_ID, OWNER_TOKEN);

        assertThat(result).isEqualTo(AiConversationLockRepository.EnsureResult.EXTENDED);
        assertThat(ttlMillis(LOCK_KEY)).isGreaterThan(SHORT_TTL_MILLIS).isLessThanOrEqualTo(CONFIGURED_TTL_MILLIS);
    }

    @Test
    void lockLost_ensure_reacquiresWithSameToken() {
        AiConversationLockRepository.EnsureResult result = lockRepository.ensure(CONVERSATION_ID, OWNER_TOKEN);

        assertThat(result).isEqualTo(AiConversationLockRepository.EnsureResult.REACQUIRED);
        assertThat(redisTemplate.opsForValue().get(LOCK_KEY)).isEqualTo(OWNER_TOKEN);
        assertThat(ttlMillis(LOCK_KEY)).isPositive();
    }

    @Test
    void lockHeldByOther_ensure_keepsOtherLock() {
        redisTemplate.opsForValue().set(LOCK_KEY, OTHER_TOKEN, SHORT_TTL_MILLIS, TimeUnit.MILLISECONDS);

        AiConversationLockRepository.EnsureResult result = lockRepository.ensure(CONVERSATION_ID, OWNER_TOKEN);

        assertThat(result).isEqualTo(AiConversationLockRepository.EnsureResult.HELD_BY_OTHER);
        assertThat(redisTemplate.opsForValue().get(LOCK_KEY)).isEqualTo(OTHER_TOKEN);
        assertThat(ttlMillis(LOCK_KEY)).isLessThanOrEqualTo(SHORT_TTL_MILLIS);
    }

    @Test
    void ownGeneratingState_extend_extendsTtl() {
        redisTemplate.opsForValue().set(GENERATING_KEY, "201", SHORT_TTL_MILLIS, TimeUnit.MILLISECONDS);

        boolean extended = generationStateRepository.extend(CONVERSATION_ID, 201L);

        assertThat(extended).isTrue();
        assertThat(ttlMillis(GENERATING_KEY)).isGreaterThan(SHORT_TTL_MILLIS);
    }

    @Test
    void otherMessageGenerating_extend_keepsOtherState() {
        redisTemplate.opsForValue().set(GENERATING_KEY, "202", SHORT_TTL_MILLIS, TimeUnit.MILLISECONDS);

        boolean extended = generationStateRepository.extend(CONVERSATION_ID, 201L);

        assertThat(extended).isFalse();
        assertThat(ttlMillis(GENERATING_KEY)).isLessThanOrEqualTo(SHORT_TTL_MILLIS);
    }

    @Test
    void generatingStateMissing_extend_returnsFalse() {
        assertThat(generationStateRepository.extend(CONVERSATION_ID, 201L)).isFalse();
        assertThat(redisTemplate.hasKey(GENERATING_KEY)).isFalse();
    }

    private long ttlMillis(String key) {
        return redisTemplate.getExpire(key, TimeUnit.MILLISECONDS);
    }
}
