package kr.ktb.zura.needu.notification.repository;

import java.time.LocalDateTime;

public record NotificationInsertRow(
        Long eventId,
        Long receiverUserId,
        LocalDateTime expiresAt
) {
}
