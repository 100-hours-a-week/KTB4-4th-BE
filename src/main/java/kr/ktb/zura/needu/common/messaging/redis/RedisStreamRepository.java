package kr.ktb.zura.needu.common.messaging.redis;

import java.util.List;
import kr.ktb.zura.needu.common.redis.StreamRedisConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

// Subscriber가 쓰는 Redis Stream 명령을 모아 둔다.
// Subscriber는 흐름 제어만, 여기서는 Redis 명령과 Redis 오류 해석만 맡는다.
@Slf4j
@Repository
class RedisStreamRepository {

    private static final String BUSY_GROUP_ERROR = "BUSYGROUP";
    private static final String NO_GROUP_ERROR = "NOGROUP";
    // 처음부터 읽는다 <- 그룹보다 먼저 발행된 메시지도 처리되도록 ($면 그룹 생성 전 메시지를 놓친다)
    private static final ReadOffset GROUP_START_OFFSET = ReadOffset.from("0");
    // KEYS[1]=streamKey, ARGV[1]=group, ARGV[2]=messageId
    static final DefaultRedisScript<Long> ACKNOWLEDGE_AND_DELETE_SCRIPT = new DefaultRedisScript<>("""
            redis.call('XACK', KEYS[1], ARGV[1], ARGV[2])
            return redis.call('XDEL', KEYS[1], ARGV[2])
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    RedisStreamRepository(@Qualifier(StreamRedisConfig.STREAM_REDIS_TEMPLATE) StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // XGROUP CREATE {streamKey} {group} 0 MKSTREAM
    // 여러 인스턴스가 동시에 기동하면 먼저 만든 쪽 외에는 BUSYGROUP이 나므로 무시한다
    void createGroupIfAbsent(String streamKey, String group) {
        try {
            redisTemplate.opsForStream().createGroup(streamKey, GROUP_START_OFFSET, group);
        } catch (DataAccessException exception) {
            if (!isRedisError(exception, BUSY_GROUP_ERROR)) {
                throw exception;
            }
        }
    }

    // XREADGROUP GROUP {group} {consumer} COUNT {count} STREAMS {streamKey} >
    // 아직 어느 소비자에게도 전달되지 않은 메시지만 가져온다. BLOCK 없이 바로 반환한다
    // 그룹이 사라졌으면(NOGROUP, Redis 재시작·키 삭제) 다시 만들고 이번 회차는 빈 결과로 넘긴다
    // read()가 StreamOffset<K>... 가변인자라 제네릭 배열 생성 경고가 난다. 오프셋 하나만 넘기고 배열을 꺼내 쓰지 않으므로 안전하다
    @SuppressWarnings("unchecked")
    List<MapRecord<String, Object, Object>> readNew(String streamKey, String group, String consumerName, int count) {
        // COUNT를 빼면 쌓인 메시지를 전부 가져오므로 0 이하는 막는다
        if (count <= 0) {
            throw new IllegalArgumentException("Stream read count must be positive. count=" + count);
        }
        List<MapRecord<String, Object, Object>> records;
        try {
            records = redisTemplate.opsForStream().read(
                    Consumer.from(group, consumerName),
                    StreamReadOptions.empty().count(count),
                    StreamOffset.create(streamKey, ReadOffset.lastConsumed()));
        } catch (DataAccessException exception) {
            if (!isRedisError(exception, NO_GROUP_ERROR)) {
                throw exception;
            }
            log.warn("Stream consumer group missing. Recreating group. streamKey={}, group={}", streamKey, group);
            createGroupIfAbsent(streamKey, group);
            return List.of();
        }
        return records == null ? List.of() : records;
    }

    void acknowledgeAndDelete(String streamKey, String group, String messageId) {
        redisTemplate.execute(ACKNOWLEDGE_AND_DELETE_SCRIPT, List.of(streamKey), group, messageId);
    }

    // Lettuce 오류는 RedisSystemException 안쪽에 감싸져 오므로 가장 안쪽 원인의 메시지로 판별한다
    private boolean isRedisError(DataAccessException exception, String errorPrefix) {
        String message = NestedExceptionUtils.getMostSpecificCause(exception).getMessage();
        return message != null && message.startsWith(errorPrefix);
    }
}
