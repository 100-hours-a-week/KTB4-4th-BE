package kr.ktb.zura.needu.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import kr.ktb.zura.needu.common.redis.RedisKeyGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RefreshTokenReuseGraceRepositoryTest {

    private static final byte[] TOKEN_HASH = {0x0a, (byte) 0xff};
    private static final String KEY = "backend:test:auth:refresh-token-reuse-grace:0aff";

    private ValueOperations<String, String> valueOperations;
    private RefreshTokenReuseGraceRepository repository;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        repository = new RefreshTokenReuseGraceRepository(redisTemplate, new RedisKeyGenerator("backend:test"));
    }

    @Test
    void rotatedTokenHash_save_writesSessionIdWithTtlUnderHexKey() {
        repository.save(TOKEN_HASH, 7L, Duration.ofSeconds(10));

        verify(valueOperations).set(KEY, "7", Duration.ofSeconds(10));
    }

    @Test
    void savedTokenHash_findSessionId_returnsSessionId() {
        when(valueOperations.get(KEY)).thenReturn("7");

        assertThat(repository.findSessionId(TOKEN_HASH)).contains(7L);
    }

    @Test
    void expiredTokenHash_findSessionId_returnsEmpty() {
        assertThat(repository.findSessionId(TOKEN_HASH)).isEmpty();
    }
}
