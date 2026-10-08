package kr.ktb.zura.needu.common.redis;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RedisKeyGeneratorTest {

    @Test
    void multipleIdentifiers_generatesColonSeparatedKey() {
        RedisKeyGenerator generator = new RedisKeyGenerator("needu:test");

        String key = generator.generate("aichat", "idempotency", 41L, "message-id");

        assertEquals("needu:test:aichat:idempotency:41:message-id", key);
    }
}
