package kr.ktb.zura.needu.notification.dto.response;

import java.time.LocalDateTime;

public record PushSubscriptionResponse(boolean enabled, LocalDateTime updatedAt) {
}
