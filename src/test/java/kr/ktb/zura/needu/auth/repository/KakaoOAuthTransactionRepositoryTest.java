package kr.ktb.zura.needu.auth.repository;

import java.time.Duration;
import java.util.Optional;
import kr.ktb.zura.needu.auth.type.KakaoOAuthContext;
import kr.ktb.zura.needu.auth.type.KakaoOAuthFlow;
import kr.ktb.zura.needu.common.redis.RedisKeyGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KakaoOAuthTransactionRepositoryTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private KakaoOAuthTransactionRepository repository;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        repository = new KakaoOAuthTransactionRepository(
                redisTemplate, new RedisKeyGenerator("backend:test"), JsonMapper.builder().build());
    }

    @Test
    void saveContext_writesJsonWithTtl() {
        repository.save(KakaoOAuthFlow.FRIEND, "state-123",
                new KakaoOAuthContext("https://needu.example.com/friends", 7L), Duration.ofMinutes(10));

        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(
                org.mockito.ArgumentMatchers.eq("backend:test:auth:kakao-oauth-friend:state-123"),
                valueCaptor.capture(), org.mockito.ArgumentMatchers.eq(Duration.ofMinutes(10)));
        assertThat(valueCaptor.getValue()).contains("https://needu.example.com/friends", "\"userId\":7");
    }

    @Test
    void existingContext_consume_usesAtomicGetAndDelete() {
        when(valueOperations.getAndDelete("backend:test:auth:kakao-oauth-login:state-123"))
                .thenReturn("{\"returnUrl\":\"https://needu.example.com/login\",\"userId\":null}");

        Optional<KakaoOAuthContext> context = repository.consume(KakaoOAuthFlow.LOGIN, "state-123");

        assertThat(context).contains(new KakaoOAuthContext("https://needu.example.com/login", null));
        verify(valueOperations).getAndDelete("backend:test:auth:kakao-oauth-login:state-123");
    }
}
