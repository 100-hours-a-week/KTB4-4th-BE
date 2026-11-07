package kr.ktb.zura.needu.aichat.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiReplyConsumerNameTest {

    @Test
    void hostName_generate_startsWithHostName() {
        assertThat(AiReplyConsumerName.generate("ip-10-0-0-1").value())
                .matches("ip-10-0-0-1-[0-9a-f]{8}");
    }

    @Test
    void sameHost_generate_createsDifferentNames() {
        AiReplyConsumerName first = AiReplyConsumerName.generate("local-mac");
        AiReplyConsumerName second = AiReplyConsumerName.generate("local-mac");

        assertThat(first).isNotEqualTo(second);
    }
}
