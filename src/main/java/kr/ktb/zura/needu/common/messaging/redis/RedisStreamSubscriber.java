package kr.ktb.zura.needu.common.messaging.redis;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import kr.ktb.zura.needu.common.messaging.MessageConsumer;
import kr.ktb.zura.needu.common.messaging.MessageSubscriber;
import org.springframework.stereotype.Component;

// 소비자 그룹으로 Redis Stream을 읽어 MessageConsumer에 넘긴다.
// 처리 성공 시 XACK, 실패가 반복되면 DLQ 스트림으로 옮긴다.
@Component
public class RedisStreamSubscriber implements MessageSubscriber {

    // topic마다 Consumer 하나만 둔다 => 같은 메시지를 어느 Consumer가 처리할지 모호하지 않게
    private final Map<String, MessageConsumer<?>> consumersByTopic = new ConcurrentHashMap<>();

    @Override
    public void subscribe(MessageConsumer<?> consumer) {
        if (consumer == null || consumer.topic() == null || consumer.topic().isBlank()) {
            throw new IllegalArgumentException("Message consumer must have a topic.");
        }
        if (consumer.payloadType() == null) {
            throw new IllegalArgumentException("Message consumer must have a payload type. topic=" + consumer.topic());
        }
        MessageConsumer<?> registered = consumersByTopic.putIfAbsent(consumer.topic(), consumer);
        if (registered != null) {
            throw new IllegalStateException("Message consumer already subscribed. topic=" + consumer.topic());
        }
    }

    // XGROUP CREATE ... MKSTREAM, 이미 있으면(BUSYGROUP) 무시한다
    private void createConsumerGroup(String streamKey) {
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    // XACK
    private void acknowledge(String streamKey, String messageId) {
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    // XAUTOCLAIM으로 죽은 소비자가 처리하다 만 메시지를 가져온다
    private void claimPendingMessages(String streamKey) {
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    // 전달 횟수가 한도를 넘은 메시지를 DLQ 스트림으로 옮기고 원래 메시지는 ACK한다
    private void moveToDeadLetter(String streamKey, String messageId) {
        throw new UnsupportedOperationException("Not implemented yet.");
    }
}
