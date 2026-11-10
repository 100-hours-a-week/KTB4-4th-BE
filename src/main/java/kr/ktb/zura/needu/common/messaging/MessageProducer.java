package kr.ktb.zura.needu.common.messaging;

public interface MessageProducer {

    void publish(String topic, Object payload);
}
