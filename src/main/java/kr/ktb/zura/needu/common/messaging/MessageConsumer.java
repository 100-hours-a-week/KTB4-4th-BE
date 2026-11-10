package kr.ktb.zura.needu.common.messaging;

public interface MessageConsumer<T> {

    String topic();

    Class<T> payloadType();

    void consume(T payload);
}
