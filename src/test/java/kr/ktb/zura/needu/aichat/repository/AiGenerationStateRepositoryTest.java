package kr.ktb.zura.needu.aichat.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.List;
import kr.ktb.zura.needu.common.redis.RedisKeyGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

@SuppressWarnings("unchecked")
class AiGenerationStateRepositoryTest {

    private static final String KEY = "backend:test:aichat:generating:101";
    private static final Duration TTL = Duration.ofSeconds(120);

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private final AiGenerationStateRepository repository = new AiGenerationStateRepository(
            redisTemplate,
            new RedisKeyGenerator("backend:test"),
            TTL
    );

    @Test
    void generationStarted_save_storesUserMessageIdWithTtl() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);

        repository.save(101L, 701L);

        verify(valueOperations).set(KEY, "701", TTL);
    }

    @Test
    void generationInProgress_findUserMessageId_returnsStoredId() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(KEY)).willReturn("701");

        assertThat(repository.findUserMessageId(101L)).contains(701L);
    }

    @Test
    void noGenerationOrTtlExpired_findUserMessageId_returnsEmpty() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(KEY)).willReturn(null);

        assertThat(repository.findUserMessageId(101L)).isEmpty();
    }

    @Test
    void generationFinished_delete_executesCompareAndDeleteScript() {
        repository.delete(101L, 701L);

        verify(redisTemplate).execute(any(RedisScript.class), eq(List.of(KEY)), eq("701"));
    }
}
