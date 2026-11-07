package kr.ktb.zura.needu.common.messaging.redis;

import kr.ktb.zura.needu.common.messaging.MessageConsumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.stream.MapRecord;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

// topic 하나를 전담해 Redis Stream을 읽고 MessageConsumer에 넘긴다.
// Spring 빈이 아니라 Subscriber가 topic마다 만들고 시작·종료한다.
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

    // 읽어 온 메시지 한 건을 Consumer에 넘기고 결과에 따라 ACK 여부를 정한다.
    // 워커 스레드에서 호출하므로 예외를 밖으로 던지지 않는다 => 한 건 실패가 다른 메시지 처리를 막지 않게
    //  - 성공: ACK + DEL
    //  - consume 예외: ACK하지 않는다 => PEL에 남아 claim이 다시 가져간다
    //  - payload를 읽을 수 없음: 다시 시도해도 같으므로 ACK + DEL하고 버린다
    void consumeMessage(MapRecord<String, Object, Object> record) {
        String messageId = record.getId().getValue();

        Object payload = record.getValue().get(RedisStreamPublisher.PAYLOAD_FIELD);
        if (payload == null) {
            log.error("Stream message payload missing. Discarding message. topic={}, messageId={}",
                    consumer.topic(), messageId);
            acknowledge(messageId);
            return;
        }

        try {
            deserializeAndConsume(consumer, String.valueOf(payload));
        } catch (JacksonException exception) {
            // 예외 메시지에 payload 일부가 담길 수 있어 예외 종류만 남긴다
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

    // payloadType으로 읽은 값을 같은 Consumer에 넘기기 위해 와일드카드를 T로 묶는다
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

    // XAUTOCLAIM으로 죽은 소비자가 처리하다 만 메시지를 가져온다
    private void claimPendingMessages() {
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    // 전달 횟수가 한도를 넘은 메시지를 DLQ 스트림으로 옮기고 원래 메시지는 ACK한다
    private void moveToDeadLetter(String messageId) {
        throw new UnsupportedOperationException("Not implemented yet.");
    }
}
