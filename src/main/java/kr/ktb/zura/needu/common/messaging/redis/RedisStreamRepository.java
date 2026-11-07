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
import org.springframework.stereotype.Repository;

@Slf4j
@Repository
class RedisStreamRepository {

    private static final String BUSY_GROUP_ERROR = "BUSYGROUP";
    private static final String NO_GROUP_ERROR = "NOGROUP";
    private static final ReadOffset GROUP_START_OFFSET = ReadOffset.from("0");

    private final StringRedisTemplate redisTemplate;

    RedisStreamRepository(@Qualifier(StreamRedisConfig.STREAM_REDIS_TEMPLATE) StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    void createGroupIfAbsent(String streamKey, String group) {
        try {
            redisTemplate.opsForStream().createGroup(streamKey, GROUP_START_OFFSET, group);
        } catch (DataAccessException exception) {
            if (!isRedisError(exception, BUSY_GROUP_ERROR)) {
                throw exception;
            }
        }
    }

    @SuppressWarnings("unchecked")
    List<MapRecord<String, Object, Object>> readNew(String streamKey, String group, String consumerName, int count) {
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
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    private boolean isRedisError(DataAccessException exception, String errorPrefix) {
        String message = NestedExceptionUtils.getMostSpecificCause(exception).getMessage();
        return message != null && message.startsWith(errorPrefix);
    }
}
