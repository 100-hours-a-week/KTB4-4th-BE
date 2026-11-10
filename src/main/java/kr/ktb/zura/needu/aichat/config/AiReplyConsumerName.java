package kr.ktb.zura.needu.aichat.config;

import java.util.UUID;

public record AiReplyConsumerName(String value) {

    private static final int RANDOM_SUFFIX_LENGTH = 8;

    public static AiReplyConsumerName generate(String hostName) {
        String suffix = UUID.randomUUID().toString().substring(0, RANDOM_SUFFIX_LENGTH);
        return new AiReplyConsumerName(hostName + "-" + suffix);
    }
}
