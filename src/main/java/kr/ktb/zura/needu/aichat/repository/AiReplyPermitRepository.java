package kr.ktb.zura.needu.aichat.repository;

import java.util.List;

import kr.ktb.zura.needu.aichat.config.AiReplyQueueProperties;
import kr.ktb.zura.needu.common.redis.RedisKeyGenerator;
import kr.ktb.zura.needu.common.redis.StreamRedisConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

// AI 서버 전체 동시 답변 수
@Repository
public class AiReplyPermitRepository {

    private static final DefaultRedisScript<Long> ACQUIRE_SCRIPT = new DefaultRedisScript<>("""
            local time = redis.call('TIME')
            local now = time[1] * 1000 + math.floor(time[2] / 1000)
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', now)
            if redis.call('ZCARD', KEYS[1]) >= tonumber(ARGV[1]) then
                return 0
            end
            redis.call('ZADD', KEYS[1], now + tonumber(ARGV[2]), ARGV[3])
            return 1
            """, Long.class);

    private static final DefaultRedisScript<Long> EXTEND_SCRIPT = new DefaultRedisScript<>("""
            local time = redis.call('TIME')
            local now = time[1] * 1000 + math.floor(time[2] / 1000)
            return redis.call('ZADD', KEYS[1], now + tonumber(ARGV[1]), ARGV[2])
            """, Long.class);

    // 만료된 허가는 다음 획득 때 지워진다 => 아직 지워지지 않은 만료 허가는 세지 않는다
    private static final DefaultRedisScript<Long> COUNT_IN_USE_SCRIPT = new DefaultRedisScript<>("""
            local time = redis.call('TIME')
            local now = time[1] * 1000 + math.floor(time[2] / 1000)
            return redis.call('ZCOUNT', KEYS[1], '(' .. now, '+inf')
            """, Long.class);

    private static final long READDED = 1;

    private final StringRedisTemplate redisTemplate;
    private final AiReplyQueueProperties properties;
    private final String key;

    public AiReplyPermitRepository(
            @Qualifier(StreamRedisConfig.STREAM_REDIS_TEMPLATE) StringRedisTemplate redisTemplate,
            RedisKeyGenerator keyGenerator,
            AiReplyQueueProperties properties
    ) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.key = keyGenerator.generate("aichat", "reply-permits");
    }

    public boolean acquire(String permitId) {
        Long result = redisTemplate.execute(ACQUIRE_SCRIPT, List.of(key),
                String.valueOf(properties.globalMaxConcurrency()),
                String.valueOf(properties.permitTtl().toMillis()),
                permitId);
        return Long.valueOf(1).equals(result);
    }

    public boolean extend(String permitId) {
        Long result = redisTemplate.execute(EXTEND_SCRIPT, List.of(key),
                String.valueOf(properties.permitTtl().toMillis()), permitId);
        return !Long.valueOf(READDED).equals(result);
    }

    public void release(String permitId) {
        redisTemplate.opsForZSet().remove(key, permitId);
    }

    public long countInUse() {
        Long result = redisTemplate.execute(COUNT_IN_USE_SCRIPT, List.of(key));
        return result == null ? 0 : result;
    }
}
