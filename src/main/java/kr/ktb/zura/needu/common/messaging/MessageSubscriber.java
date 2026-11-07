package kr.ktb.zura.needu.common.messaging;

// MessageConsumer를 메시지 큐에 연결한다. ACK·재시도·DLQ 같은 큐 고유 동작은 구현체 안에만 둔다.
public interface MessageSubscriber {

    void subscribe(MessageConsumer<?> consumer);
}
