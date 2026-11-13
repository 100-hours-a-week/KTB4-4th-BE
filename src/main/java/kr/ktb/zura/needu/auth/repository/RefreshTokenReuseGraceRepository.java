package kr.ktb.zura.needu.auth.repository;

import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import kr.ktb.zura.needu.common.redis.RedisKeyGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

// 교체된 리프레시 토큰 해시 → 세션 ID를 유예 시간 동안 보관한다. 원문 토큰은 키에 넣지 않는다.
@Repository
@RequiredArgsConstructor
public class RefreshTokenReuseGraceRepository {

    private final StringRedisTemplate redisTemplate;
    private final RedisKeyGenerator keyGenerator;

    public void save(byte[] rotatedRefreshTokenHash, Long sessionId, Duration ttl) {
        redisTemplate.opsForValue().set(generateKey(rotatedRefreshTokenHash), String.valueOf(sessionId), ttl);
    }

    public Optional<Long> findSessionId(byte[] rotatedRefreshTokenHash) {
        String sessionId = redisTemplate.opsForValue().get(generateKey(rotatedRefreshTokenHash));
        return Optional.ofNullable(sessionId).map(Long::valueOf);
    }

    private String generateKey(byte[] rotatedRefreshTokenHash) {
        return keyGenerator.generate("auth", "refresh-token-reuse-grace",
                HexFormat.of().formatHex(rotatedRefreshTokenHash));
    }
}
