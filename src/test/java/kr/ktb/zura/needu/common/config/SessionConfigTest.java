package kr.ktb.zura.needu.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.RedisSerializer;

class SessionConfigTest {

    @Test
    void springSessionDefaultRedisSerializer_roundTripsSessionAttributesAsJson() {
        RedisSerializer<Object> serializer = new SessionConfig().springSessionDefaultRedisSerializer();

        byte[] serialized = serializer.serialize("state-123");

        assertThat(serializer.deserialize(serialized)).isEqualTo("state-123");
    }
}
