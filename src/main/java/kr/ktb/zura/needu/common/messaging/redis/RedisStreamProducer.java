package kr.ktb.zura.needu.common.messaging.redis;

import java.util.Map;
import kr.ktb.zura.needu.common.messaging.MessageProducer;
import kr.ktb.zura.needu.common.redis.RedisKeyGenerator;
import kr.ktb.zura.needu.common.redis.StreamRedisConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisStreamCommands.TrimOptions;
import org.springframework.data.redis.connection.RedisStreamCommands.XAddOptions;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
public class RedisStreamProducer implements MessageProducer {

    static final String PAYLOAD_FIELD = "payload";

    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;
    private final RedisKeyGenerator keyGenerator;
    private final long maxLength;

    public RedisStreamProducer(
            @Qualifier(StreamRedisConfig.STREAM_REDIS_TEMPLATE) StringRedisTemplate redisTemplate,
            JsonMapper jsonMapper,
            RedisKeyGenerator keyGenerator,
            @Value("${needu.messaging.stream.max-length}") long maxLength
    ) {
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
        RecordId recordId = redisTemplate.opsForStream().add(
                streamKey,
                Map.of(PAYLOAD_FIELD, serialize(payload)),
                XAddOptions.trim(TrimOptions.maxLen(maxLength).approximate())
        );
        log.debug("Stream message published. topic={}, recordId={}", topic, recordId);
    }

    private String toStreamKey(String topic) {
        return RedisStreamNames.toStreamKey(keyGenerator, topic);
    }

    private String serialize(Object payload) {
        try {
            return jsonMapper.writeValueAsString(payload);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Failed to serialize stream message payload.", exception);
        }
    }
}
