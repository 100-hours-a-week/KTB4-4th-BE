package kr.ktb.zura.needu.notification.dto.request;

import java.time.LocalDateTime;
import kr.ktb.zura.needu.notification.type.NotificationCategory;

public record NotificationSearchCondition(
        NotificationCategory category, // category가 null이면 전체 조회 (ALL)
        String cursor,
        int size,
        LocalDateTime since
) {
}
