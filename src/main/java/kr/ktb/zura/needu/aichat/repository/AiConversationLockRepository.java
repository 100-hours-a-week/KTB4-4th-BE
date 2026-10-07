package kr.ktb.zura.needu.aichat.repository;

import kr.ktb.zura.needu.aichat.config.AiConversationLockProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

// Redis 락 획득과 소유자 확인 후 해제는 이 Repository에서만 구현한다.
@Repository
public class AiConversationLockRepository {

    private final StringRedisTemplate redisTemplate;
    private final AiConversationLockProperties properties;

    public AiConversationLockRepository(
            StringRedisTemplate redisTemplate,
            AiConversationLockProperties properties
    ) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }
}
