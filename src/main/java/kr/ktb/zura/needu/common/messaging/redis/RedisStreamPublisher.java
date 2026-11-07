package kr.ktb.zura.needu.common.messaging.redis;

import java.util.Map;
import kr.ktb.zura.needu.common.messaging.MessagePublisher;
import kr.ktb.zura.needu.common.redis.RedisKeyGenerator;
import kr.ktb.zura.needu.common.redis.StreamRedisConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisStreamCommands.XAddOptions;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

// topic을 Redis Stream 키로 바꿔 XADD로 발행한다.
// 엔트리는 payload 필드 하나에 JSON 문자열을 담는다 => 다른 MQ로 바꿔도 메시지 형식을 그대로 옮길 수 있다.
// 발행 실패는 그대로 던진다. XADD가 timeout이어도 실제로는 저장됐을 수 있어 여기서 재시도하지 않는다.
@Slf4j
@Component
public class RedisStreamPublisher implements MessagePublisher {

    static final String PAYLOAD_FIELD = "payload";

    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;
    private final RedisKeyGenerator keyGenerator;
    private final long maxLength;

    public RedisStreamPublisher(
            @Qualifier(StreamRedisConfig.STREAM_REDIS_TEMPLATE) StringRedisTemplate redisTemplate,
            JsonMapper jsonMapper,
            RedisKeyGenerator keyGenerator,
            @Value("${needu.messaging.stream.max-length}") long maxLength
    ) {
        // 0 이하면 XADD MAXLEN이 스트림을 비워 버리므로 기동 시점에 막는다
        if (maxLength <= 0) {
            throw new IllegalArgumentException(
                    "needu.messaging.stream.max-length must be positive. value=" + maxLength);
        }
        this.redisTemplate = redisTemplate;
        this.jsonMapper = jsonMapper;
        this.keyGenerator = keyGenerator;
        this.maxLength = maxLength;
    }

    @Override
    public void publish(String topic, Object payload) {
        String streamKey = toStreamKey(topic);
        // 근사 trimming(MAXLEN ~)이라 정확히 자르지 않고 Redis가 효율적인 단위로 오래된 엔트리를 지운다
        RecordId recordId = redisTemplate.opsForStream().add(
                streamKey,
                Map.of(PAYLOAD_FIELD, serialize(payload)),
                XAddOptions.maxlen(maxLength).approximateTrimming(true)
        );
        // 메시지 원문에는 알림 본문이 담기므로 식별자만 남긴다
        log.debug("Stream message published. topic={}, recordId={}", topic, recordId);
    }

    private String toStreamKey(String topic) {
        return keyGenerator.generate("messaging", "stream", topic);
    }

    private String serialize(Object payload) {
        try {
            return jsonMapper.writeValueAsString(payload);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Failed to serialize stream message payload.", exception);
        }
    }
}
