package kr.ktb.zura.needu.notification.dto.response;

import java.time.LocalDateTime;

public record NotificationSettingResponse(
        boolean friendJoined,
        boolean friendBirthday,
        boolean anniversaryEvent,
        boolean marketing,
        LocalDateTime updatedAt
) {
}
