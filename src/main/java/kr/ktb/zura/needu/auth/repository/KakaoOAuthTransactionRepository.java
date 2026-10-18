package kr.ktb.zura.needu.auth.repository;

import java.time.Duration;
import java.util.Optional;
import kr.ktb.zura.needu.auth.type.KakaoOAuthContext;
import kr.ktb.zura.needu.auth.type.KakaoOAuthFlow;
import kr.ktb.zura.needu.common.redis.RedisKeyGenerator;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class KakaoOAuthTransactionRepository {

    private final StringRedisTemplate redisTemplate;
    private final RedisKeyGenerator keyGenerator;
    private final JsonMapper jsonMapper;

    public KakaoOAuthTransactionRepository(
            StringRedisTemplate redisTemplate,
            RedisKeyGenerator keyGenerator,
            JsonMapper jsonMapper
    ) {
        this.redisTemplate = redisTemplate;
        this.keyGenerator = keyGenerator;
        this.jsonMapper = jsonMapper;
    }

    public void save(KakaoOAuthFlow flow, String state, KakaoOAuthContext context, Duration ttl) {
        try {
            String value = jsonMapper.writeValueAsString(context);
            redisTemplate.opsForValue().set(generateKey(flow, state), value, ttl);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Failed to serialize Kakao OAuth transaction.", exception);
        }
    }

    public Optional<KakaoOAuthContext> consume(KakaoOAuthFlow flow, String state) {
        String value = redisTemplate.opsForValue().getAndDelete(generateKey(flow, state));
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(jsonMapper.readValue(value, KakaoOAuthContext.class));
        } catch (JacksonException exception) {
            throw new IllegalStateException("Failed to deserialize Kakao OAuth transaction.", exception);
        }
    }

    private String generateKey(KakaoOAuthFlow flow, String state) {
        return keyGenerator.generate("auth", "kakao-oauth-" + flow.key(), state);
    }
}
