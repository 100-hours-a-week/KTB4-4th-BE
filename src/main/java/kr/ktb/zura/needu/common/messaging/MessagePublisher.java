package kr.ktb.zura.needu.common.messaging;

// 메시지 큐에 메시지를 발행한다. 구현체(Redis Stream, Kafka, RabbitMQ)를 바꿔도 호출부는 그대로 둔다.
public interface MessagePublisher {

    void publish(String topic, Object payload);
}
