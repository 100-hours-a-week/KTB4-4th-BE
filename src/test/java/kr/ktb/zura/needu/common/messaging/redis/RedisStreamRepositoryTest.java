package kr.ktb.zura.needu.common.messaging.redis;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

class RedisStreamRepositoryTest {

    private static final String STREAM_KEY = "backend:test:messaging:stream:webpush";
    private static final String GROUP = "webpush-workers";

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final StreamOperations<String, Object, Object> streamOperations = mock(StreamOperations.class);

    private RedisStreamRepository repository;

    @BeforeEach
    void setUp() {
        when(redisTemplate.<Object, Object>opsForStream()).thenReturn(streamOperations);
        repository = new RedisStreamRepository(redisTemplate);
    }

    @Test
    void createGroupIfAbsent_createsGroupReadingFromStreamStart() {
        repository.createGroupIfAbsent(STREAM_KEY, GROUP);

        verify(streamOperations).createGroup(STREAM_KEY, ReadOffset.from("0"), GROUP);
    }

    @Test
    void createGroupIfAbsent_ignoresExistingGroup() {
        when(streamOperations.createGroup(anyString(), any(ReadOffset.class), anyString()))
                .thenThrow(redisError("BUSYGROUP Consumer Group name already exists"));

        assertThatCode(() -> repository.createGroupIfAbsent(STREAM_KEY, GROUP))
                .doesNotThrowAnyException();
    }

    @Test
    void createGroupIfAbsent_propagatesOtherRedisErrors() {
        when(streamOperations.createGroup(anyString(), any(ReadOffset.class), anyString()))
                .thenThrow(redisError("WRONGTYPE Operation against a key holding the wrong kind of value"));

        assertThatThrownBy(() -> repository.createGroupIfAbsent(STREAM_KEY, GROUP))
                .isInstanceOf(RedisSystemException.class);
    }

    // Lettuce 오류가 Spring 예외에 감싸져 오는 형태를 흉내 낸다
    private RedisSystemException redisError(String message) {
        return new RedisSystemException("Redis error", new RuntimeException(message));
    }
}
