package kr.ktb.zura.needu.friend.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import kr.ktb.zura.needu.common.redis.RedisKeyGenerator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

@SuppressWarnings("unchecked")
class PokeCooldownRepositoryTest {

    private static final String KEY = "backend:test:friend:poke:1:2";
    private static final String REQUEST_ID = "550e8400-e29b-41d4-a716-446655440000";

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private final PokeCooldownRepository repository = new PokeCooldownRepository(
            redisTemplate,
            new RedisKeyGenerator("backend:test")
    );

    @Test
    void keyMissing_create_reservesCooldownWithRequestId() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.setIfAbsent(KEY, REQUEST_ID, Duration.ofHours(1))).willReturn(true);

        boolean created = repository.create(1L, 2L, REQUEST_ID, Duration.ofHours(1));

        assertThat(created).isTrue();
        verify(valueOperations).setIfAbsent(KEY, REQUEST_ID, Duration.ofHours(1));
    }

    @Test
    void keyExists_findRemainingSeconds_returnsRedisTtl() {
        given(redisTemplate.getExpire(KEY, TimeUnit.SECONDS)).willReturn(3540L);

        long remainingSeconds = repository.findRemainingSeconds(1L, 2L);

        assertThat(remainingSeconds).isEqualTo(3540L);
    }

    @Test
    void reservationOwnedByRequest_delete_executesCompareAndDeleteScript() {
        repository.delete(1L, 2L, REQUEST_ID);

        verify(redisTemplate).execute(
                ArgumentMatchers.<RedisScript<Long>>any(),
                org.mockito.ArgumentMatchers.eq(List.of(KEY)),
                org.mockito.ArgumentMatchers.eq(REQUEST_ID)
        );
    }
}
