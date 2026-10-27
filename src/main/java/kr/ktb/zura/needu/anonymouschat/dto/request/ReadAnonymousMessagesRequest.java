package kr.ktb.zura.needu.anonymouschat.dto.request;

import jakarta.validation.constraints.NotNull;

public record ReadAnonymousMessagesRequest(@NotNull Long lastReadMessageId) {
}
