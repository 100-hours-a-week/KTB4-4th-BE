package kr.ktb.zura.needu.common.messaging.redis;

import java.util.List;
import kr.ktb.zura.needu.common.redis.StreamRedisConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

// Subscriber가 쓰는 Redis Stream 명령을 모아 둔다.
// Subscriber는 흐름 제어만, 여기서는 Redis 명령과 Redis 오류 해석만 맡는다.
@Repository
class RedisStreamRepository {

    private static final String BUSY_GROUP_ERROR = "BUSYGROUP";
    // 처음부터 읽는다 <- 그룹보다 먼저 발행된 메시지도 처리되도록 ($면 그룹 생성 전 메시지를 놓친다)
    private static final ReadOffset GROUP_START_OFFSET = ReadOffset.from("0");

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
    List<MapRecord<String, Object, Object>> readNew(String streamKey, String group, String consumerName, int count) {
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    // Lua로 XACK + XDEL을 한 번에 실행한다
    void acknowledgeAndDelete(String streamKey, String group, String messageId) {
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    // Lettuce 오류는 RedisSystemException 안쪽에 감싸져 오므로 가장 안쪽 원인의 메시지로 판별한다
    private boolean isRedisError(DataAccessException exception, String errorPrefix) {
        String message = NestedExceptionUtils.getMostSpecificCause(exception).getMessage();
        return message != null && message.startsWith(errorPrefix);
    }
}
