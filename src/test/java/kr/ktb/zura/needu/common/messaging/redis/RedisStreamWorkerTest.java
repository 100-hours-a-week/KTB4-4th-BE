package kr.ktb.zura.needu.common.messaging.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import kr.ktb.zura.needu.common.messaging.MessageConsumer;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import tools.jackson.databind.json.JsonMapper;

class RedisStreamWorkerTest {

    private static final String STREAM_KEY = "backend:test:messaging:stream:webpush";
    private static final String GROUP = "webpush-workers";

    private final RedisStreamRepository streamRepository = mock(RedisStreamRepository.class);
    private final RecordingConsumer consumer = new RecordingConsumer();
    private final RedisStreamWorker worker =
            new RedisStreamWorker(consumer, streamRepository, new JsonMapper(), STREAM_KEY, GROUP);

    @Test
    void consumeMessage_passesDeserializedPayloadAndAcknowledges() {
        worker.consumeMessage(record("1-0", "{\"notificationId\":1,\"receiverUserIds\":[10,20]}"));

        assertThat(consumer.received).containsExactly(new TestMessage(1L, List.of(10L, 20L)));
        verify(streamRepository).acknowledgeAndDelete(STREAM_KEY, GROUP, "1-0");
    }

    @Test
    void consumeMessage_leavesMessagePendingWhenConsumeFails() {
        consumer.failure = new IllegalStateException("push server down");

        assertThatCode(() -> worker.consumeMessage(record("1-0", "{\"notificationId\":1}")))
                .doesNotThrowAnyException();
        verify(streamRepository, never()).acknowledgeAndDelete(anyString(), anyString(), anyString());
    }

    @Test
    void consumeMessage_discardsUnreadablePayload() {
        worker.consumeMessage(record("1-0", "not-json"));

        assertThat(consumer.received).isEmpty();
        verify(streamRepository).acknowledgeAndDelete(STREAM_KEY, GROUP, "1-0");
    }

    @Test
    void consumeMessage_discardsMessageWithoutPayloadField() {
        worker.consumeMessage(MapRecord.<String, Object, Object>create(STREAM_KEY, Map.of("other", "x"))
                .withId(RecordId.of("1-0")));

        assertThat(consumer.received).isEmpty();
        verify(streamRepository).acknowledgeAndDelete(STREAM_KEY, GROUP, "1-0");
    }

    @Test
    void consumeMessage_doesNotThrowWhenAcknowledgeFails() {
        doThrow(new RedisSystemException("timeout", new RuntimeException()))
                .when(streamRepository).acknowledgeAndDelete(STREAM_KEY, GROUP, "1-0");

        assertThatCode(() -> worker.consumeMessage(record("1-0", "{\"notificationId\":1}")))
                .doesNotThrowAnyException();
    }

    private MapRecord<String, Object, Object> record(String id, String payloadJson) {
        return MapRecord.<String, Object, Object>create(STREAM_KEY, Map.of(RedisStreamPublisher.PAYLOAD_FIELD, payloadJson))
                .withId(RecordId.of(id));
    }

    private record TestMessage(Long notificationId, List<Long> receiverUserIds) {
    }

    private static class RecordingConsumer implements MessageConsumer<TestMessage> {

        private final List<TestMessage> received = new CopyOnWriteArrayList<>();
        private volatile RuntimeException failure;

        @Override
        public String topic() {
            return "webpush";
        }

        @Override
        public Class<TestMessage> payloadType() {
            return TestMessage.class;
        }

        @Override
        public void consume(TestMessage payload) {
            if (failure != null) {
                throw failure;
            }
            received.add(payload);
        }
    }
}
