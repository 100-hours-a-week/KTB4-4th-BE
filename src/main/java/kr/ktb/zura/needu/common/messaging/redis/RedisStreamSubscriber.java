package kr.ktb.zura.needu.common.messaging.redis;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import kr.ktb.zura.needu.common.messaging.MessageConsumer;
import kr.ktb.zura.needu.common.messaging.MessageSubscriber;
import kr.ktb.zura.needu.common.redis.RedisKeyGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

// Consumer를 등록하면 topic마다 워커를 하나 만들고, 애플리케이션 생명주기에 맞춰 워커를 시작·종료한다.
// 메시지를 읽고 처리하는 일은 RedisStreamWorker가 맡는다.
@Slf4j
@Component
@EnableConfigurationProperties(RedisStreamWorkerProperties.class)
public class RedisStreamSubscriber implements MessageSubscriber, SmartLifecycle {

    // topic마다 워커 하나만 둔다 => 같은 메시지를 어느 Consumer가 처리할지 모호하지 않게
    private final Map<String, RedisStreamWorker> workersByTopic = new ConcurrentHashMap<>();

    private final RedisStreamRepository streamRepository;
    private final RedisKeyGenerator keyGenerator;
    private final JsonMapper jsonMapper;
    private final RedisStreamWorkerProperties properties;
    // 그룹 안에서 이 인스턴스를 구분하는 이름이다. 재기동하면 새 이름을 쓰고, 이전 이름에 남은 메시지는 claim이 가져간다
    private final String consumerName;
    private volatile boolean running;

    RedisStreamSubscriber(
            RedisStreamRepository streamRepository,
            RedisKeyGenerator keyGenerator,
            JsonMapper jsonMapper,
            RedisStreamWorkerProperties properties
    ) {
        this.streamRepository = streamRepository;
        this.keyGenerator = keyGenerator;
        this.jsonMapper = jsonMapper;
        this.properties = properties;
        this.consumerName = createConsumerName();
    }

    @Override
    public synchronized void subscribe(MessageConsumer<?> consumer) {
        if (consumer == null || consumer.topic() == null || consumer.topic().isBlank()) {
            throw new IllegalArgumentException("Message consumer must have a topic.");
        }
        if (consumer.payloadType() == null) {
            throw new IllegalArgumentException("Message consumer must have a payload type. topic=" + consumer.topic());
        }
        if (workersByTopic.containsKey(consumer.topic())) {
            throw new IllegalStateException("Message consumer already subscribed. topic=" + consumer.topic());
        }
        RedisStreamWorker worker = createWorker(consumer);
        workersByTopic.put(consumer.topic(), worker);
        // 이미 기동한 뒤에 등록되면 바로 시작한다
        if (running) {
            worker.start();
        }
    }

    @Override
    public synchronized void start() {
        if (!properties.enabled()) {
            log.info("Stream worker disabled. Skipping message consumption.");
            return;
        }
        running = true;
        workersByTopic.values().forEach(RedisStreamWorker::start);
    }

    // 워커마다 처리 중인 메시지를 기다린다. 지금은 topic이 하나라 차례로 멈춘다
    @Override
    public synchronized void stop() {
        running = false;
        workersByTopic.values().forEach(RedisStreamWorker::stop);
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private RedisStreamWorker createWorker(MessageConsumer<?> consumer) {
        return new RedisStreamWorker(
                consumer,
                streamRepository,
                jsonMapper,
                properties,
                RedisStreamNames.toStreamKey(keyGenerator, consumer.topic()),
                RedisStreamNames.toGroupName(consumer.topic()),
                consumerName);
    }

    // ECS 태스크는 HOSTNAME이 태스크마다 다르다. 같은 호스트에서 여러 개 띄워도 겹치지 않게 랜덤 값을 붙인다
    private static String createConsumerName() {
        String hostname = System.getenv("HOSTNAME");
        String prefix = hostname == null || hostname.isBlank() ? "local" : hostname;
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
