package kr.ktb.zura.needu.common.messaging.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
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

    @Test
    @SuppressWarnings("unchecked")
    void readNew_readsUndeliveredMessagesAsGroupConsumer() {
        MapRecord<String, Object, Object> record = MapRecord.<String, Object, Object>create(
                STREAM_KEY, Map.of("payload", "{}")).withId(RecordId.of("1-0"));
        when(streamOperations.read(any(Consumer.class), any(StreamReadOptions.class), any(StreamOffset.class)))
                .thenReturn(List.of(record));

        List<MapRecord<String, Object, Object>> records = repository.readNew(STREAM_KEY, GROUP, "worker-1", 10);

        ArgumentCaptor<Consumer> consumer = ArgumentCaptor.forClass(Consumer.class);
        ArgumentCaptor<StreamReadOptions> options = ArgumentCaptor.forClass(StreamReadOptions.class);
        ArgumentCaptor<StreamOffset<String>> offset = ArgumentCaptor.forClass(StreamOffset.class);
        verify(streamOperations).read(consumer.capture(), options.capture(), offset.capture());
        assertThat(consumer.getValue()).isEqualTo(Consumer.from(GROUP, "worker-1"));
        assertThat(options.getValue().getCount()).isEqualTo(10L);
        assertThat(options.getValue().getBlock()).isNull();
        assertThat(offset.getValue().getKey()).isEqualTo(STREAM_KEY);
        assertThat(offset.getValue().getOffset()).isEqualTo(ReadOffset.lastConsumed());
        assertThat(records).containsExactly(record);
    }

    @Test
    void readNew_returnsEmptyListWhenRedisReturnsNull() {
        when(streamOperations.read(any(Consumer.class), any(StreamReadOptions.class), any(StreamOffset.class)))
                .thenReturn(null);

        assertThat(repository.readNew(STREAM_KEY, GROUP, "worker-1", 10)).isEmpty();
    }

    @Test
    void readNew_recreatesGroupAndReturnsEmptyWhenGroupMissing() {
        when(streamOperations.read(any(Consumer.class), any(StreamReadOptions.class), any(StreamOffset.class)))
                .thenThrow(redisError("NOGROUP No such key or consumer group"));

        assertThat(repository.readNew(STREAM_KEY, GROUP, "worker-1", 10)).isEmpty();
        verify(streamOperations).createGroup(STREAM_KEY, ReadOffset.from("0"), GROUP);
    }

    @Test
    void readNew_propagatesOtherRedisErrors() {
        when(streamOperations.read(any(Consumer.class), any(StreamReadOptions.class), any(StreamOffset.class)))
                .thenThrow(redisError("WRONGTYPE Operation against a key holding the wrong kind of value"));

        assertThatThrownBy(() -> repository.readNew(STREAM_KEY, GROUP, "worker-1", 10))
                .isInstanceOf(RedisSystemException.class);
        verify(streamOperations, never()).createGroup(anyString(), any(ReadOffset.class), anyString());
    }

    @Test
    void readNew_rejectsNonPositiveCount() {
        assertThatThrownBy(() -> repository.readNew(STREAM_KEY, GROUP, "worker-1", 0))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(streamOperations);
    }

    @Test
    void acknowledgeAndDelete_runsAckAndDeleteInOneScript() {
        repository.acknowledgeAndDelete(STREAM_KEY, GROUP, "1-0");

        verify(redisTemplate).execute(
                RedisStreamRepository.ACKNOWLEDGE_AND_DELETE_SCRIPT, List.of(STREAM_KEY), GROUP, "1-0");
        assertThat(RedisStreamRepository.ACKNOWLEDGE_AND_DELETE_SCRIPT.getScriptAsString())
                .contains("XACK", "XDEL");
    }

    @Test
    void acknowledgeAndDelete_propagatesRedisFailure() {
        when(redisTemplate.execute(
                RedisStreamRepository.ACKNOWLEDGE_AND_DELETE_SCRIPT, List.of(STREAM_KEY), GROUP, "1-0"))
                .thenThrow(redisError("timeout"));

        assertThatThrownBy(() -> repository.acknowledgeAndDelete(STREAM_KEY, GROUP, "1-0"))
                .isInstanceOf(RedisSystemException.class);
    }

    // Lettuce 오류가 Spring 예외에 감싸져 오는 형태를 흉내 낸다
    private RedisSystemException redisError(String message) {
        return new RedisSystemException("Redis error", new RuntimeException(message));
    }
}
