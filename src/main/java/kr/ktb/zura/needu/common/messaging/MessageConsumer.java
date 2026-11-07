package kr.ktb.zura.needu.common.messaging;

// 한 topic의 메시지를 처리한다. 정상 종료하면 처리 완료(ACK), 예외를 던지면 재시도 대상이다.
public interface MessageConsumer<T> {

    String topic();

    Class<T> payloadType();

    void consume(T payload);
}
