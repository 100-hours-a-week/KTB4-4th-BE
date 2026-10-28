package kr.ktb.zura.needu.anonymouschat.ws;

import com.fasterxml.jackson.annotation.JsonInclude;

public record AnonymousMessageFailedEvent(
        Long roomId,
        String clientMessageId,
        @JsonInclude(JsonInclude.Include.NON_NULL) Long retryAfterSeconds
) {
}
