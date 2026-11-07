package kr.ktb.zura.needu.common.messaging.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import kr.ktb.zura.needu.common.messaging.MessageConsumer;
import kr.ktb.zura.needu.common.redis.RedisKeyGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class RedisStreamSubscriberTest {

    private final RedisStreamRepository streamRepository = mock(RedisStreamRepository.class);
    private RedisStreamSubscriber subscriber = createSubscriber(true);

    @AfterEach
    void tearDown() {
        subscriber.stop();
    }

    @Test
    void subscribe_registersConsumersOfDifferentTopics() {
        assertThatCode(() -> {
            subscriber.subscribe(new TestConsumer("webpush", String.class));
            subscriber.subscribe(new TestConsumer("email", String.class));
        }).doesNotThrowAnyException();
    }

    @Test
    void subscribe_rejectsSecondConsumerOfSameTopic() {
        subscriber.subscribe(new TestConsumer("webpush", String.class));

        assertThatThrownBy(() -> subscriber.subscribe(new TestConsumer("webpush", String.class)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void subscribe_rejectsConsumerWithoutTopic() {
        assertThatThrownBy(() -> subscriber.subscribe(new TestConsumer(" ", String.class)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> subscriber.subscribe(new TestConsumer(null, String.class)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> subscriber.subscribe(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void subscribe_rejectsConsumerWithoutPayloadType() {
        assertThatThrownBy(() -> subscriber.subscribe(new TestConsumer("webpush", null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void subscribe_doesNotReadBeforeStart() {
        subscriber.subscribe(new TestConsumer("webpush", String.class));

        verifyNoInteractions(streamRepository);
    }

    @Test
    void start_startsWorkerOfEachTopic() {
        when(streamRepository.readNew(anyString(), anyString(), anyString(), anyInt())).thenReturn(List.of());
        subscriber.subscribe(new TestConsumer("webpush", String.class));
        subscriber.subscribe(new TestConsumer("email", String.class));

        subscriber.start();

        assertThat(subscriber.isRunning()).isTrue();
        verify(streamRepository).createGroupIfAbsent("backend:test:messaging:stream:webpush", "webpush-workers");
        verify(streamRepository).createGroupIfAbsent("backend:test:messaging:stream:email", "email-workers");
        verify(streamRepository, timeout(1_000).atLeastOnce())
                .readNew(eq("backend:test:messaging:stream:webpush"), eq("webpush-workers"), anyString(), anyInt());
        verify(streamRepository, timeout(1_000).atLeastOnce())
                .readNew(eq("backend:test:messaging:stream:email"), eq("email-workers"), anyString(), anyInt());
    }

    @Test
    void subscribe_startsWorkerImmediatelyWhenAlreadyRunning() {
        when(streamRepository.readNew(anyString(), anyString(), anyString(), anyInt())).thenReturn(List.of());
        subscriber.start();

        subscriber.subscribe(new TestConsumer("webpush", String.class));

        verify(streamRepository).createGroupIfAbsent("backend:test:messaging:stream:webpush", "webpush-workers");
    }

    @Test
    void start_doesNothingWhenWorkerDisabled() {
        subscriber = createSubscriber(false);
        subscriber.subscribe(new TestConsumer("webpush", String.class));

        subscriber.start();

        assertThat(subscriber.isRunning()).isFalse();
        verifyNoInteractions(streamRepository);
    }

    @Test
    void stop_stopsRunning() {
        when(streamRepository.readNew(anyString(), anyString(), anyString(), anyInt())).thenReturn(List.of());
        subscriber.subscribe(new TestConsumer("webpush", String.class));
        subscriber.start();

        subscriber.stop();

        assertThat(subscriber.isRunning()).isFalse();
    }

    private RedisStreamSubscriber createSubscriber(boolean enabled) {
        RedisStreamWorkerProperties properties = new RedisStreamWorkerProperties(
                enabled, 2, 10, Duration.ofMillis(20), Duration.ofSeconds(2));
        return new RedisStreamSubscriber(
                streamRepository, new RedisKeyGenerator("backend:test"), new JsonMapper(), properties);
    }

    private record TestConsumer(String topic, Class<String> payloadType) implements MessageConsumer<String> {

        @Override
        public void consume(String payload) {
        }
    }
}
