package kr.ktb.zura.needu.notification.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WebPushResultTest {

    @Test
    void pushServiceStatusCode_returnsMatchingResult() {
        assertThat(WebPushResult.fromStatusCode(201)).isEqualTo(WebPushResult.SUCCESS);
        assertThat(WebPushResult.fromStatusCode(404)).isEqualTo(WebPushResult.EXPIRED);
        assertThat(WebPushResult.fromStatusCode(410)).isEqualTo(WebPushResult.EXPIRED);
        assertThat(WebPushResult.fromStatusCode(400)).isEqualTo(WebPushResult.FAILED);
        assertThat(WebPushResult.fromStatusCode(429)).isEqualTo(WebPushResult.FAILED);
        assertThat(WebPushResult.fromStatusCode(500)).isEqualTo(WebPushResult.FAILED);
    }
}
