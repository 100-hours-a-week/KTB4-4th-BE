package kr.ktb.zura.needu.aichat.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.List;
import kr.ktb.zura.needu.aichat.config.AiConversationLockProperties;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

@SuppressWarnings("unchecked")
class AiConversationLockRepositoryTest {

    private static final String KEY = "backend:test:aichat:lock:101";
    private static final String OWNER_TOKEN = "550e8400-e29b-41d4-a716-446655440000";
    private static final Duration TTL = Duration.ofSeconds(120);

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private final AiConversationLockRepository repository = new AiConversationLockRepository(
            redisTemplate,
            new AiConversationLockProperties(TTL, "backend:test:aichat:lock")
    );

    @Test
    void keyMissing_create_setsOwnerTokenWithTtl() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.setIfAbsent(KEY, OWNER_TOKEN, TTL)).willReturn(true);

        boolean created = repository.create(101L, OWNER_TOKEN);

        assertThat(created).isTrue();
    }

    @Test
    void keyExists_create_returnsFalse() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.setIfAbsent(KEY, OWNER_TOKEN, TTL)).willReturn(false);

        boolean created = repository.create(101L, OWNER_TOKEN);

        assertThat(created).isFalse();
    }

    @Test
    void redisReturnsNull_create_returnsFalse() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.setIfAbsent(KEY, OWNER_TOKEN, TTL)).willReturn(null);

        boolean created = repository.create(101L, OWNER_TOKEN);

        assertThat(created).isFalse();
    }

    @Test
    void lockOwnedByToken_delete_executesCompareAndDeleteScript() {
        repository.delete(101L, OWNER_TOKEN);

        verify(redisTemplate).execute(any(RedisScript.class), eq(List.of(KEY)), eq(OWNER_TOKEN));
    }
}
