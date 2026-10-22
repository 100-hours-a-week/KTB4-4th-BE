package kr.ktb.zura.needu.notification.service;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.Collection;
import java.util.UUID;
import kr.ktb.zura.needu.common.redis.RedisKeyGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class NotificationPresenceRegistry {

    // heartbeat를 두 번 놓쳐도 접속 정보가 유지되게 한다
    private static final int TTL_HEARTBEAT_MULTIPLIER = 3;

    private final StringRedisTemplate redisTemplate;
    private final String instanceId = UUID.randomUUID().toString();
    private final String instanceKey;
    private final String instancesKey;
    private final Duration ttl;
    private volatile boolean registered;

    public NotificationPresenceRegistry(
            StringRedisTemplate redisTemplate,
            RedisKeyGenerator keyGenerator,
            @Value("${needu.notification.heartbeat-interval}") Duration heartbeatInterval
    ) {
        this.redisTemplate = redisTemplate;
        this.instanceKey = keyGenerator.generate("notification", "online", instanceId);
        this.instancesKey = keyGenerator.generate("notification", "online-instances");
        this.ttl = heartbeatInterval.multipliedBy(TTL_HEARTBEAT_MULTIPLIER);
    }

    public void register(Long userId) {
        try {
            redisTemplate.opsForSet().add(instanceKey, String.valueOf(userId));
            touch();
        } catch (RuntimeException exception) {
            log.warn("Notification presence register failed. userId={}", userId, exception);
        }
    }

    public void unregister(Long userId) {
        try {
            redisTemplate.opsForSet().remove(instanceKey, String.valueOf(userId));
        } catch (RuntimeException exception) {
            log.warn("Notification presence unregister failed. userId={}", userId, exception);
        }
    }

    public void refresh(Collection<Long> connectedUserIds) {
        if (connectedUserIds.isEmpty()) {
            return;
        }
        try {
            redisTemplate.opsForSet().add(instanceKey,
                    connectedUserIds.stream().map(String::valueOf).toArray(String[]::new));
            touch();
        } catch (RuntimeException exception) {
            log.warn("Notification presence refresh failed. connectedUserCount={}",
                    connectedUserIds.size(), exception);
        }
    }

    private void touch() {
        redisTemplate.expire(instanceKey, ttl);
        redisTemplate.opsForZSet().add(instancesKey, instanceId, System.currentTimeMillis());
        redisTemplate.expire(instancesKey, ttl);
        registered = true;
    }

    @PreDestroy
    void removeInstance() {
        if (!registered) {
            return;
        }
        try {
            redisTemplate.delete(instanceKey);
            redisTemplate.opsForZSet().remove(instancesKey, instanceId);
        } catch (RuntimeException exception) {
            log.warn("Notification presence cleanup failed. instanceId={}", instanceId, exception);
        }
    }
}
