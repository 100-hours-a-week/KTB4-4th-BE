package kr.ktb.zura.needu.common.messaging.redis;

import kr.ktb.zura.needu.common.messaging.MessageConsumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.stream.MapRecord;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
final class RedisStreamWorker {

    private final MessageConsumer<?> consumer;
    private final RedisStreamRepository streamRepository;
    private final JsonMapper jsonMapper;
    private final String streamKey;
    private final String group;

    RedisStreamWorker(
            MessageConsumer<?> consumer,
            RedisStreamRepository streamRepository,
            JsonMapper jsonMapper,
            String streamKey,
            String group
    ) {
        this.consumer = consumer;
        this.streamRepository = streamRepository;
        this.jsonMapper = jsonMapper;
        this.streamKey = streamKey;
        this.group = group;
    }

    void consumeMessage(MapRecord<String, Object, Object> record) {
        String messageId = record.getId().getValue();

        Object payload = record.getValue().get(RedisStreamProducer.PAYLOAD_FIELD);
        if (payload == null) {
            log.error("Stream message payload missing. Discarding message. topic={}, messageId={}",
                    consumer.topic(), messageId);
            acknowledge(messageId);
            return;
        }

        try {
            deserializeAndConsume(consumer, String.valueOf(payload));
        } catch (JacksonException exception) {
            log.error("Stream message payload unreadable. Discarding message. topic={}, messageId={}, exception={}",
                    consumer.topic(), messageId, exception.getClass().getSimpleName());
            acknowledge(messageId);
            return;
        } catch (RuntimeException exception) {
            log.warn("Stream message consume failed. Leaving message pending. topic={}, messageId={}",
                    consumer.topic(), messageId, exception);
            return;
        }
        acknowledge(messageId);
    }

    private <T> void deserializeAndConsume(MessageConsumer<T> consumer, String payloadJson) {
        T payload = jsonMapper.readValue(payloadJson, consumer.payloadType());
        consumer.consume(payload);
    }

    private void acknowledge(String messageId) {
        try {
            streamRepository.acknowledgeAndDelete(streamKey, group, messageId);
        } catch (DataAccessException exception) {
            log.warn("Stream message acknowledge failed. topic={}, messageId={}", consumer.topic(), messageId, exception);
        }
    }

    private void claimPendingMessages() {
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    private void moveToDeadLetter(String messageId) {
        throw new UnsupportedOperationException("Not implemented yet.");
    }
}
