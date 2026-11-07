package kr.ktb.zura.needu.common.messaging.redis;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import kr.ktb.zura.needu.common.messaging.MessageConsumer;
import kr.ktb.zura.needu.common.redis.RedisKeyGenerator;
import org.junit.jupiter.api.Test;

class RedisStreamSubscriberTest {

    private final RedisStreamSubscriber subscriber = new RedisStreamSubscriber(
            mock(RedisStreamRepository.class), new RedisKeyGenerator("backend:test"));

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

    private record TestConsumer(String topic, Class<String> payloadType) implements MessageConsumer<String> {

        @Override
        public void consume(String payload) {
        }
    }
}
