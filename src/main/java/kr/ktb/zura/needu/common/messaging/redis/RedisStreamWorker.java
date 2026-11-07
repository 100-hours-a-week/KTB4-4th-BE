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

// topic 하나를 전담해 Redis Stream을 읽고 MessageConsumer에 넘긴다.
// Spring 빈이 아니라 Subscriber가 topic마다 만들고 시작·종료한다.
// topic마다 슬롯(세마포어)이 따로 있어 한 topic 처리가 느려도 다른 topic은 영향을 받지 않는다.
@Slf4j
final class RedisStreamWorker {

    private final MessageConsumer<?> consumer;
    private final RedisStreamRepository streamRepository;
    private final JsonMapper jsonMapper;
    private final RedisStreamWorkerProperties properties;
    private final String streamKey;
    private final String group;
    private final String consumerName;

    // 처리 중인 메시지 수를 concurrency로 제한한다. 비어 있는 슬롯 수만큼만 읽어 온다
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
        // 그룹 생성이 실패해도(Redis 일시 장애) 기동은 계속한다. 읽을 때 NOGROUP이면 다시 만든다
        try {
            streamRepository.createGroupIfAbsent(streamKey, group);
        } catch (DataAccessException exception) {
            log.warn("Stream consumer group create failed. Retrying on read. topic={}", consumer.topic(), exception);
        }
        poller = Thread.ofVirtual().name("stream-" + consumer.topic() + "-poller").start(this::pollMessages);
        log.info("Stream worker started. topic={}, consumerName={}", consumer.topic(), consumerName);
    }

    // 새로 읽는 것을 멈추고, 처리 중인 메시지는 shutdownTimeout까지 기다린다.
    // 시간 안에 못 끝난 메시지는 중단한다. ACK 전이라 PEL에 남아 다시 처리된다
    synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;
        // 슬롯 대기나 poll 간격 sleep 중인 poller를 깨운다
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

    // 슬롯이 하나라도 비면 비어 있는 만큼(최대 maxReadCount) 읽어 와 가상 스레드로 처리한다.
    // 읽을 게 없거나 Redis 오류면 pollInterval만큼 쉬었다가 다시 읽는다
    private void pollMessages() {
        while (running) {
            int reserved = 0;
            try {
                reserved = reserveSlots();
                List<MapRecord<String, Object, Object>> records =
                        streamRepository.readNew(streamKey, group, consumerName, reserved);
                // 읽은 수만큼만 슬롯을 쓰고 나머지는 돌려준다
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

    // 첫 슬롯은 빌 때까지 기다리고, 이어서 바로 비어 있는 슬롯을 maxReadCount까지 더 잡는다
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
            // 종료 중이라 실행하지 못했다. ACK 전이라 PEL에 남아 다시 처리된다
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

    // 읽어 온 메시지 한 건을 Consumer에 넘기고 결과에 따라 ACK 여부를 정한다.
    // 워커 스레드에서 호출하므로 예외를 밖으로 던지지 않는다 => 한 건 실패가 다른 메시지 처리를 막지 않게
    //  - 성공: ACK + DEL
    //  - consume 예외: ACK하지 않는다 => PEL에 남아 claim이 다시 가져간다
    //  - payload를 읽을 수 없음: 다시 시도해도 같으므로 ACK + DEL하고 버린다
    void consumeMessage(MapRecord<String, Object, Object> record) {
        String messageId = record.getId().getValue();

        Object payload = record.getValue().get(RedisStreamPublisher.PAYLOAD_FIELD);
        if (payload == null) {
            log.error("Stream message payload missing. Discarding message. topic={}, messageId={}",
                    consumer.topic(), messageId);
            acknowledge(messageId);
            return;
        }

        try {
            deserializeAndConsume(consumer, String.valueOf(payload));
        } catch (JacksonException exception) {
            // 예외 메시지에 payload 일부가 담길 수 있어 예외 종류만 남긴다
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

    // payloadType으로 읽은 값을 같은 Consumer에 넘기기 위해 와일드카드를 T로 묶는다
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

    // XAUTOCLAIM으로 죽은 소비자가 처리하다 만 메시지를 가져온다
    private void claimPendingMessages() {
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    // 전달 횟수가 한도를 넘은 메시지를 DLQ 스트림으로 옮기고 원래 메시지는 ACK한다
    private void moveToDeadLetter(String messageId) {
        throw new UnsupportedOperationException("Not implemented yet.");
    }
}
