package kr.ktb.zura.needu.common.messaging;

public interface MessageConsumerRegistry {

    void register(MessageConsumer<?> consumer);
}
