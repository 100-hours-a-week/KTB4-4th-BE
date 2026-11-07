package kr.ktb.zura.needu.common.messaging.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import kr.ktb.zura.needu.common.messaging.MessageConsumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import tools.jackson.databind.json.JsonMapper;

class RedisStreamWorkerTest {

    private static final String STREAM_KEY = "backend:test:messaging:stream:webpush";
    private static final String GROUP = "webpush-workers";
    private static final String CONSUMER_NAME = "worker-1";

    private final RedisStreamRepository streamRepository = mock(RedisStreamRepository.class);
    private final RecordingConsumer consumer = new RecordingConsumer();
    private RedisStreamWorker worker;

    @AfterEach
    void tearDown() {
        if (worker != null) {
            worker.stop();
        }
    }

    @Test
    void start_createsGroupAndConsumesReadMessages() {
        worker = createWorker(2, 10);
        when(streamRepository.readNew(eq(STREAM_KEY), eq(GROUP), eq(CONSUMER_NAME), anyInt()))
                .thenReturn(List.of(record("1-0", "{\"notificationId\":1}")))
                .thenReturn(List.of());

        worker.start();

        verify(streamRepository).createGroupIfAbsent(STREAM_KEY, GROUP);
        verify(streamRepository, timeout(1_000)).acknowledgeAndDelete(STREAM_KEY, GROUP, "1-0");
        assertThat(consumer.received).containsExactly(new TestMessage(1L, null));
    }

    @Test
    void start_readsNoMoreThanFreeSlots() {
        worker = createWorker(2, 10);
        when(streamRepository.readNew(eq(STREAM_KEY), eq(GROUP), eq(CONSUMER_NAME), anyInt())).thenReturn(List.of());

        worker.start();

        verify(streamRepository, timeout(1_000)).readNew(STREAM_KEY, GROUP, CONSUMER_NAME, 2);
    }

    @Test
    void start_readsNoMoreThanMaxReadCount() {
        worker = createWorker(8, 3);
        when(streamRepository.readNew(eq(STREAM_KEY), eq(GROUP), eq(CONSUMER_NAME), anyInt())).thenReturn(List.of());

        worker.start();

        verify(streamRepository, timeout(1_000)).readNew(STREAM_KEY, GROUP, CONSUMER_NAME, 3);
    }

    @Test
    void start_stopsReadingWhileAllSlotsAreBusy() throws InterruptedException {
        worker = createWorker(1, 10);
        consumer.blocker = new CountDownLatch(1);
        when(streamRepository.readNew(eq(STREAM_KEY), eq(GROUP), eq(CONSUMER_NAME), anyInt()))
                .thenReturn(List.of(record("1-0", "{\"notificationId\":1}")))
                .thenReturn(List.of());

        worker.start();
        assertThat(consumer.started.await(1, TimeUnit.SECONDS)).isTrue();
        Thread.sleep(200);

        // 슬롯 하나가 처리 중이라 두 번째 읽기를 하지 않는다
        verify(streamRepository).readNew(eq(STREAM_KEY), eq(GROUP), eq(CONSUMER_NAME), anyInt());
        consumer.blocker.countDown();
        verify(streamRepository, timeout(1_000).times(2)).readNew(STREAM_KEY, GROUP, CONSUMER_NAME, 1);
    }

    @Test
    void start_keepsReadingAfterRedisFailure() {
        worker = createWorker(2, 10);
        when(streamRepository.readNew(eq(STREAM_KEY), eq(GROUP), eq(CONSUMER_NAME), anyInt()))
                .thenThrow(new RedisSystemException("timeout", new RuntimeException()))
                .thenReturn(List.of(record("1-0", "{\"notificationId\":1}")))
                .thenReturn(List.of());

        worker.start();

        verify(streamRepository, timeout(1_000)).acknowledgeAndDelete(STREAM_KEY, GROUP, "1-0");
    }

    @Test
    void start_continuesWhenGroupCreationFails() {
        worker = createWorker(2, 10);
        doThrow(new RedisSystemException("connection refused", new RuntimeException()))
                .when(streamRepository).createGroupIfAbsent(STREAM_KEY, GROUP);
        when(streamRepository.readNew(eq(STREAM_KEY), eq(GROUP), eq(CONSUMER_NAME), anyInt())).thenReturn(List.of());

        assertThatCode(() -> worker.start()).doesNotThrowAnyException();
        verify(streamRepository, timeout(1_000).atLeast(1)).readNew(STREAM_KEY, GROUP, CONSUMER_NAME, 2);
    }

    @Test
    void stop_waitsForMessagesInProgress() throws InterruptedException {
        worker = createWorker(2, 10);
        consumer.delay = Duration.ofMillis(300);
        when(streamRepository.readNew(eq(STREAM_KEY), eq(GROUP), eq(CONSUMER_NAME), anyInt()))
                .thenReturn(List.of(record("1-0", "{\"notificationId\":1}")))
                .thenReturn(List.of());
        worker.start();
        assertThat(consumer.started.await(1, TimeUnit.SECONDS)).isTrue();

        worker.stop();

        assertThat(worker.isRunning()).isFalse();
        verify(streamRepository).acknowledgeAndDelete(STREAM_KEY, GROUP, "1-0");
    }

    @Test
    void consumeMessage_passesDeserializedPayloadAndAcknowledges() {
        worker = createWorker(2, 10);

        worker.consumeMessage(record("1-0", "{\"notificationId\":1,\"receiverUserIds\":[10,20]}"));

        assertThat(consumer.received).containsExactly(new TestMessage(1L, List.of(10L, 20L)));
        verify(streamRepository).acknowledgeAndDelete(STREAM_KEY, GROUP, "1-0");
    }

    @Test
    void consumeMessage_leavesMessagePendingWhenConsumeFails() {
        worker = createWorker(2, 10);
        consumer.failure = new IllegalStateException("push server down");

        assertThatCode(() -> worker.consumeMessage(record("1-0", "{\"notificationId\":1}")))
                .doesNotThrowAnyException();
        verify(streamRepository, never()).acknowledgeAndDelete(anyString(), anyString(), anyString());
    }

    @Test
    void consumeMessage_discardsUnreadablePayload() {
        worker = createWorker(2, 10);

        worker.consumeMessage(record("1-0", "not-json"));

        assertThat(consumer.received).isEmpty();
        verify(streamRepository).acknowledgeAndDelete(STREAM_KEY, GROUP, "1-0");
    }

    @Test
    void consumeMessage_discardsMessageWithoutPayloadField() {
        worker = createWorker(2, 10);

        worker.consumeMessage(MapRecord.<String, Object, Object>create(STREAM_KEY, Map.of("other", "x"))
                .withId(RecordId.of("1-0")));

        assertThat(consumer.received).isEmpty();
        verify(streamRepository).acknowledgeAndDelete(STREAM_KEY, GROUP, "1-0");
    }

    @Test
    void consumeMessage_doesNotThrowWhenAcknowledgeFails() {
        worker = createWorker(2, 10);
        doThrow(new RedisSystemException("timeout", new RuntimeException()))
                .when(streamRepository).acknowledgeAndDelete(STREAM_KEY, GROUP, "1-0");

        assertThatCode(() -> worker.consumeMessage(record("1-0", "{\"notificationId\":1}")))
                .doesNotThrowAnyException();
    }

    private RedisStreamWorker createWorker(int concurrency, int maxReadCount) {
        RedisStreamWorkerProperties properties = new RedisStreamWorkerProperties(
                true, concurrency, maxReadCount, Duration.ofMillis(20), Duration.ofSeconds(2));
        return new RedisStreamWorker(
                consumer, streamRepository, new JsonMapper(), properties, STREAM_KEY, GROUP, CONSUMER_NAME);
    }

    private MapRecord<String, Object, Object> record(String id, String payloadJson) {
        return MapRecord.<String, Object, Object>create(STREAM_KEY, Map.of(RedisStreamPublisher.PAYLOAD_FIELD, payloadJson))
                .withId(RecordId.of(id));
    }

    private record TestMessage(Long notificationId, List<Long> receiverUserIds) {
    }

    private static class RecordingConsumer implements MessageConsumer<TestMessage> {

        private final List<TestMessage> received = new CopyOnWriteArrayList<>();
        private final CountDownLatch started = new CountDownLatch(1);
        private volatile RuntimeException failure;
        private volatile CountDownLatch blocker;
        private volatile Duration delay;

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
            started.countDown();
            try {
                if (blocker != null) {
                    blocker.await(2, TimeUnit.SECONDS);
                }
                if (delay != null) {
                    Thread.sleep(delay);
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
            if (failure != null) {
                throw failure;
            }
            received.add(payload);
        }
    }
}
