package kr.ktb.zura.needu.common.messaging.redis;

import kr.ktb.zura.needu.common.redis.RedisKeyGenerator;

final class RedisStreamNames {

    private RedisStreamNames() {
    }

    static String toStreamKey(RedisKeyGenerator keyGenerator, String topic) {
        return keyGenerator.generate("messaging", "stream", topic);
    }
}
