package kr.ktb.zura.needu.common.messaging.redis;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import kr.ktb.zura.needu.common.messaging.MessageConsumer;
import org.junit.jupiter.api.Test;

class RedisStreamConsumerRegistryTest {

    private final RedisStreamConsumerRegistry registry = new RedisStreamConsumerRegistry();

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

    private record TestConsumer(String topic, Class<String> payloadType) implements MessageConsumer<String> {

        @Override
        public void consume(String payload) {
        }
    }
}
