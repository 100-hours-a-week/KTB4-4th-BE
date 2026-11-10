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

class RedisStreamConsumerRegistryTest {

    private final RedisStreamRepository streamRepository = mock(RedisStreamRepository.class);
    private RedisStreamConsumerRegistry registry = createRegistry(true);

    @AfterEach
    void tearDown() {
        registry.stop();
    }

    @Test
    void register_registersConsumersOfDifferentTopics() {
        assertThatCode(() -> {
            registry.register(new TestConsumer("webpush", String.class));
            registry.register(new TestConsumer("email", String.class));
        }).doesNotThrowAnyException();
    }

    @Test
    void register_rejectsSecondConsumerOfSameTopic() {
        registry.register(new TestConsumer("webpush", String.class));

        assertThatThrownBy(() -> registry.register(new TestConsumer("webpush", String.class)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void register_rejectsConsumerWithoutTopic() {
        assertThatThrownBy(() -> registry.register(new TestConsumer(" ", String.class)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> registry.register(new TestConsumer(null, String.class)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> registry.register(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void register_rejectsConsumerWithoutPayloadType() {
        assertThatThrownBy(() -> registry.register(new TestConsumer("webpush", null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void register_doesNotReadBeforeStart() {
        registry.register(new TestConsumer("webpush", String.class));

        verifyNoInteractions(streamRepository);
    }

    @Test
    void start_startsWorkerOfEachTopic() {
        when(streamRepository.readNew(anyString(), anyString(), anyString(), anyInt())).thenReturn(List.of());
        registry.register(new TestConsumer("webpush", String.class));
        registry.register(new TestConsumer("email", String.class));

        registry.start();

        assertThat(registry.isRunning()).isTrue();
        verify(streamRepository).createGroupIfAbsent("backend:test:messaging:stream:webpush", "webpush-workers");
        verify(streamRepository).createGroupIfAbsent("backend:test:messaging:stream:email", "email-workers");
        verify(streamRepository, timeout(1_000).atLeastOnce())
                .readNew(eq("backend:test:messaging:stream:webpush"), eq("webpush-workers"), anyString(), anyInt());
        verify(streamRepository, timeout(1_000).atLeastOnce())
                .readNew(eq("backend:test:messaging:stream:email"), eq("email-workers"), anyString(), anyInt());
    }

    @Test
    void register_startsWorkerImmediatelyWhenAlreadyRunning() {
        when(streamRepository.readNew(anyString(), anyString(), anyString(), anyInt())).thenReturn(List.of());
        registry.start();

        registry.register(new TestConsumer("webpush", String.class));

        verify(streamRepository).createGroupIfAbsent("backend:test:messaging:stream:webpush", "webpush-workers");
    }

    @Test
    void start_doesNothingWhenWorkerDisabled() {
        registry = createRegistry(false);
        registry.register(new TestConsumer("webpush", String.class));

        registry.start();

        assertThat(registry.isRunning()).isFalse();
        verifyNoInteractions(streamRepository);
    }

    @Test
    void stop_stopsRunning() {
        when(streamRepository.readNew(anyString(), anyString(), anyString(), anyInt())).thenReturn(List.of());
        registry.register(new TestConsumer("webpush", String.class));
        registry.start();

        registry.stop();

        assertThat(registry.isRunning()).isFalse();
    }

    private RedisStreamConsumerRegistry createRegistry(boolean enabled) {
        RedisStreamWorkerProperties properties = new RedisStreamWorkerProperties(
                enabled, 2, 10, Duration.ofMillis(20), Duration.ofSeconds(2));
        return new RedisStreamConsumerRegistry(
                streamRepository, new RedisKeyGenerator("backend:test"), new JsonMapper(), properties);
    }

    private record TestConsumer(String topic, Class<String> payloadType) implements MessageConsumer<String> {

        @Override
        public void consume(String payload) {
        }
    }
}
