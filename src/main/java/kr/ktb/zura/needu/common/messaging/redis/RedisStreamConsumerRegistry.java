package kr.ktb.zura.needu.common.messaging.redis;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import kr.ktb.zura.needu.common.messaging.MessageConsumer;
import kr.ktb.zura.needu.common.messaging.MessageConsumerRegistry;
import kr.ktb.zura.needu.common.redis.RedisKeyGenerator;
import org.springframework.stereotype.Component;

@Component
public class RedisStreamConsumerRegistry implements MessageConsumerRegistry {

    private final Map<String, MessageConsumer<?>> consumersByTopic = new ConcurrentHashMap<>();

    private final RedisStreamRepository streamRepository;
    private final RedisKeyGenerator keyGenerator;

    RedisStreamConsumerRegistry(RedisStreamRepository streamRepository, RedisKeyGenerator keyGenerator) {
        this.streamRepository = streamRepository;
        this.keyGenerator = keyGenerator;
    }

    @Override
    public void register(MessageConsumer<?> consumer) {
        if (consumer == null || consumer.topic() == null || consumer.topic().isBlank()) {
            throw new IllegalArgumentException("Message consumer must have a topic.");
        }
        if (consumer.payloadType() == null) {
            throw new IllegalArgumentException("Message consumer must have a payload type. topic=" + consumer.topic());
        }
        MessageConsumer<?> registered = consumersByTopic.putIfAbsent(consumer.topic(), consumer);
        if (registered != null) {
            throw new IllegalStateException("Message consumer already registered. topic=" + consumer.topic());
        }
    }

    private void createConsumerGroup(String topic) {
        streamRepository.createGroupIfAbsent(
                RedisStreamNames.toStreamKey(keyGenerator, topic),
                RedisStreamNames.toGroupName(topic));
    }

    private void acknowledge(String streamKey, String messageId) {
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    private void claimPendingMessages(String streamKey) {
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    private void moveToDeadLetter(String streamKey, String messageId) {
        throw new UnsupportedOperationException("Not implemented yet.");
    }
}
