package kr.ktb.zura.needu.common.messaging.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import kr.ktb.zura.needu.common.redis.RedisKeyGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.RedisStreamCommands.XAddOptions;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.json.JsonMapper;

class RedisStreamProducerTest {

    private static final long MAX_LENGTH = 100_000L;

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final StreamOperations<String, Object, Object> streamOperations = mock(StreamOperations.class);
    private final JsonMapper jsonMapper = new JsonMapper();

    private RedisStreamProducer producer;

    @BeforeEach
    void setUp() {
        when(redisTemplate.<Object, Object>opsForStream()).thenReturn(streamOperations);
        when(streamOperations.add(anyString(), anyMap(), any(XAddOptions.class)))
                .thenReturn(RecordId.of("1730941200000-0"));
        producer = new RedisStreamProducer(
                redisTemplate, jsonMapper, new RedisKeyGenerator("backend:test"), MAX_LENGTH);
    }

    @Test
    void publish_addsPayloadJsonToTopicStream() {
        producer.publish("webpush", new TestMessage(1L, List.of(10L, 20L)));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> body = ArgumentCaptor.forClass(Map.class);
        verify(streamOperations).add(eq("backend:test:messaging:stream:webpush"), body.capture(), any(XAddOptions.class));
        assertThat(body.getValue()).containsOnlyKeys(RedisStreamProducer.PAYLOAD_FIELD);
        assertThat(jsonMapper.readValue(body.getValue().get(RedisStreamProducer.PAYLOAD_FIELD), TestMessage.class))
                .isEqualTo(new TestMessage(1L, List.of(10L, 20L)));
    }

    @Test
    void publish_trimsStreamApproximatelyToMaxLength() {
        producer.publish("webpush", new TestMessage(1L, List.of()));

        ArgumentCaptor<XAddOptions> options = ArgumentCaptor.forClass(XAddOptions.class);
        verify(streamOperations).add(anyString(), anyMap(), options.capture());
        assertThat(options.getValue().getMaxlen()).isEqualTo(MAX_LENGTH);
        assertThat(options.getValue().isApproximateTrimming()).isTrue();
    }

    @Test
    void publish_throwsWhenPayloadCannotBeSerialized() {
        assertThatThrownBy(() -> producer.publish("webpush", new UnserializableMessage()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Failed to serialize stream message payload.");
        verifyNoInteractions(streamOperations);
    }

    @Test
    void publish_propagatesRedisFailureWithoutRetry() {
        when(streamOperations.add(anyString(), anyMap(), any(XAddOptions.class)))
                .thenThrow(new RedisSystemException("timeout", new RuntimeException()));

        assertThatThrownBy(() -> producer.publish("webpush", new TestMessage(1L, List.of())))
                .isInstanceOf(RedisSystemException.class);
        verify(streamOperations).add(anyString(), anyMap(), any(XAddOptions.class));
    }

    @Test
    void create_rejectsNonPositiveMaxLength() {
        assertThatThrownBy(() -> new RedisStreamProducer(
                redisTemplate, jsonMapper, new RedisKeyGenerator("backend:test"), 0L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private record TestMessage(Long notificationId, List<Long> receiverUserIds) {
    }

    // 직렬화 중 값을 읽다가 실패하는 payload
    private static class UnserializableMessage {

        public String getValue() {
            throw new IllegalStateException("cannot read value");
        }
    }
}
