package kr.ktb.zura.needu.common.messaging.redis;

import kr.ktb.zura.needu.common.redis.RedisKeyGenerator;

// Publisher와 Subscriber가 같은 topic을 같은 스트림으로 보도록 이름 규칙을 한곳에 둔다.
final class RedisStreamNames {

    private RedisStreamNames() {
    }

    // "webpush" => {prefix}:messaging:stream:webpush
    static String toStreamKey(RedisKeyGenerator keyGenerator, String topic) {
        return keyGenerator.generate("messaging", "stream", topic);
    }

    // "webpush" => webpush-workers
    // 그룹은 스트림 키 아래에 속하므로 prefix 없이 topic만으로 구분한다
    static String toGroupName(String topic) {
        return topic + "-workers";
    }
}
