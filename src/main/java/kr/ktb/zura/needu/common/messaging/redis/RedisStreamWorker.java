package kr.ktb.zura.needu.common.messaging.redis;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import kr.ktb.zura.needu.common.messaging.MessageConsumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.stream.MapRecord;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
final class RedisStreamWorker {

    private final MessageConsumer<?> consumer;
    private final RedisStreamRepository streamRepository;
    private final JsonMapper jsonMapper;
    private final RedisStreamWorkerProperties properties;
    private final String streamKey;
    private final String group;
    private final String consumerName;

    private final Semaphore slots;
    private final ExecutorService executor;
    private volatile boolean running;
    private Thread poller;

    RedisStreamWorker(
            MessageConsumer<?> consumer,
            RedisStreamRepository streamRepository,
            JsonMapper jsonMapper,
            RedisStreamWorkerProperties properties,
            String streamKey,
            String group,
            String consumerName
    ) {
        this.consumer = consumer;
        this.streamRepository = streamRepository;
        this.jsonMapper = jsonMapper;
        this.properties = properties;
        this.streamKey = streamKey;
        this.group = group;
        this.consumerName = consumerName;
        this.slots = new Semaphore(properties.concurrency());
        this.executor = Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("stream-" + consumer.topic() + "-", 0).factory());
    }

    synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        try {
            streamRepository.createGroupIfAbsent(streamKey, group);
        } catch (DataAccessException exception) {
            log.warn("Stream consumer group create failed. Retrying on read. topic={}", consumer.topic(), exception);
        }
        poller = Thread.ofVirtual().name("stream-" + consumer.topic() + "-poller").start(this::pollMessages);
        log.info("Stream worker started. topic={}, consumerName={}", consumer.topic(), consumerName);
    }

    synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;
        poller.interrupt();
        executor.shutdown();
        try {
            poller.join(properties.shutdownTimeout().toMillis());
            if (!executor.awaitTermination(properties.shutdownTimeout().toMillis(), TimeUnit.MILLISECONDS)) {
                log.warn("Stream messages interrupted on shutdown. topic={}, remaining={}",
                        consumer.topic(), properties.concurrency() - slots.availablePermits());
                executor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("Stream worker stopped. topic={}", consumer.topic());
    }

    boolean isRunning() {
        return running;
    }

    private void pollMessages() {
        while (running) {
            int reserved = 0;
            try {
                reserved = reserveSlots();
                List<MapRecord<String, Object, Object>> records =
                        streamRepository.readNew(streamKey, group, consumerName, reserved);
                slots.release(reserved - records.size());
                reserved = 0;
                records.forEach(this::dispatch);
                if (records.isEmpty()) {
                    Thread.sleep(properties.pollInterval());
                }
            } catch (InterruptedException exception) {
                slots.release(reserved);
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException exception) {
                slots.release(reserved);
                if (!running) {
                    return;
                }
                log.warn("Stream read failed. topic={}", consumer.topic(), exception);
                if (!sleepQuietly()) {
                    return;
                }
            }
        }
    }

    private int reserveSlots() throws InterruptedException {
        slots.acquire();
        int reserved = 1;
        while (reserved < properties.maxReadCount() && slots.tryAcquire()) {
            reserved++;
        }
        return reserved;
    }

    private void dispatch(MapRecord<String, Object, Object> record) {
        try {
            executor.execute(() -> {
                try {
                    consumeMessage(record);
                } finally {
                    slots.release();
                }
            });
        } catch (RuntimeException exception) {
            slots.release();
        }
    }

    private boolean sleepQuietly() {
        try {
            Thread.sleep(properties.pollInterval());
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    void consumeMessage(MapRecord<String, Object, Object> record) {
        String messageId = record.getId().getValue();

        Object payload = record.getValue().get(RedisStreamProducer.PAYLOAD_FIELD);
        if (payload == null) {
            log.error("Stream message payload missing. Discarding message. topic={}, messageId={}",
                    consumer.topic(), messageId);
            acknowledge(messageId);
            return;
        }

        try {
            deserializeAndConsume(consumer, String.valueOf(payload));
        } catch (JacksonException exception) {
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

    private void claimPendingMessages() {
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    private void moveToDeadLetter(String messageId) {
        throw new UnsupportedOperationException("Not implemented yet.");
    }
}
