package kr.ktb.zura.needu.common.redis;

import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RedisKeyGenerator {

    private final String prefix;

    public RedisKeyGenerator(@Value("${needu.redis.key-prefix}") String prefix) {
        this.prefix = prefix;
    }

    public String generate(String domain, String purpose, Object... identifiers) {
        return Stream.concat(
                Stream.of(prefix, domain, purpose),
                Arrays.stream(identifiers).map(String::valueOf)
        ).collect(Collectors.joining(":"));
    }
}
