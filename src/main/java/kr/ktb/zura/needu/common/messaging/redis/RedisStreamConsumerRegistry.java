package kr.ktb.zura.needu.common.messaging.redis;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import kr.ktb.zura.needu.common.messaging.MessageConsumer;
import kr.ktb.zura.needu.common.messaging.MessageConsumerRegistry;
import kr.ktb.zura.needu.common.redis.RedisKeyGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
@EnableConfigurationProperties(RedisStreamWorkerProperties.class)
public class RedisStreamConsumerRegistry implements MessageConsumerRegistry, SmartLifecycle {

    private final Map<String, RedisStreamWorker> workersByTopic = new ConcurrentHashMap<>();

    private final RedisStreamRepository streamRepository;
    private final RedisKeyGenerator keyGenerator;
    private final JsonMapper jsonMapper;
    private final RedisStreamWorkerProperties properties;
    private final String consumerName;
    private volatile boolean running;

    RedisStreamConsumerRegistry(
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
    public synchronized void register(MessageConsumer<?> consumer) {
        if (consumer == null || consumer.topic() == null || consumer.topic().isBlank()) {
            throw new IllegalArgumentException("Message consumer must have a topic.");
        }
        if (consumer.payloadType() == null) {
            throw new IllegalArgumentException("Message consumer must have a payload type. topic=" + consumer.topic());
        }
        if (workersByTopic.containsKey(consumer.topic())) {
            throw new IllegalStateException("Message consumer already registered. topic=" + consumer.topic());
        }
        RedisStreamWorker worker = createWorker(consumer);
        workersByTopic.put(consumer.topic(), worker);
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

    private static String createConsumerName() {
        String hostname = System.getenv("HOSTNAME");
        String prefix = hostname == null || hostname.isBlank() ? "local" : hostname;
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
