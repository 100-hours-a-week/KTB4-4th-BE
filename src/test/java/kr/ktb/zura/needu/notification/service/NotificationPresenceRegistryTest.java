package kr.ktb.zura.needu.notification.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import kr.ktb.zura.needu.common.redis.RedisKeyGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

class NotificationPresenceRegistryTest {

    private static final String INSTANCE_KEY_PREFIX = "needu:test:notification:online:";
    private static final String INSTANCES_KEY = "needu:test:notification:online-instances";

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final SetOperations<String, String> setOperations = mock(SetOperations.class);
    @SuppressWarnings("unchecked")
    private final ZSetOperations<String, String> zSetOperations = mock(ZSetOperations.class);
    private final NotificationPresenceRegistry registry;

    NotificationPresenceRegistryTest() {
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        registry = new NotificationPresenceRegistry(
                redisTemplate, new RedisKeyGenerator("needu:test"), Duration.ofSeconds(25));
    }

    @Test
    void connectedUser_register_addsUserToInstanceSetWithTtl() {
        registry.register(1L);

        verify(setOperations).add(startsWith(INSTANCE_KEY_PREFIX), eq("1"));
        verify(redisTemplate).expire(startsWith(INSTANCE_KEY_PREFIX), eq(Duration.ofSeconds(75)));
        verify(zSetOperations).add(eq(INSTANCES_KEY), anyString(), anyDouble());
        verify(redisTemplate).expire(INSTANCES_KEY, Duration.ofSeconds(75));
    }

    @Test
    void disconnectedUser_unregister_removesUserFromInstanceSet() {
        registry.unregister(1L);

        verify(setOperations).remove(startsWith(INSTANCE_KEY_PREFIX), eq("1"));
    }

    @Test
    void connectedUsers_refresh_addsAllUsersAndExtendsTtl() {
        registry.refresh(List.of(1L, 2L));

        verify(setOperations).add(startsWith(INSTANCE_KEY_PREFIX), eq("1"), eq("2"));
        verify(redisTemplate).expire(startsWith(INSTANCE_KEY_PREFIX), eq(Duration.ofSeconds(75)));
    }

    @Test
    void noConnectedUser_refresh_skipsRedis() {
        registry.refresh(List.of());

        verifyNoInteractions(redisTemplate);
    }

    @Test
    void redisFails_register_doesNotThrow() {
        when(setOperations.add(anyString(), any(String[].class)))
                .thenThrow(new RedisConnectionFailureException("down"));

        assertThatCode(() -> registry.register(1L)).doesNotThrowAnyException();
    }

    @Test
    void registeredInstance_removeInstance_deletesInstanceKeys() {
        registry.register(1L);

        registry.removeInstance();

        verify(redisTemplate).delete(startsWith(INSTANCE_KEY_PREFIX));
        verify(zSetOperations).remove(eq(INSTANCES_KEY), anyString());
    }

    @Test
    void neverRegistered_removeInstance_skipsRedis() {
        registry.removeInstance();

        verify(redisTemplate, never()).delete(anyString());
    }
}
