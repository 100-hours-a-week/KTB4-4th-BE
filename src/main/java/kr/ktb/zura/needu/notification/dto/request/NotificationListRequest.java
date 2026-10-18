package kr.ktb.zura.needu.notification.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDateTime;
import kr.ktb.zura.needu.notification.type.NotificationCategory;
import org.springframework.format.annotation.DateTimeFormat;

public record NotificationListRequest(
        @Pattern(regexp = "CHAT|POKE|EVENT|FRIEND_JOINED|PURCHASE_STATUS") String category,
        String cursor,
        @Min(1) @Max(50) Integer size,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime since
) {

    public NotificationSearchCondition toCondition(int defaultSize) {
        return new NotificationSearchCondition(
                category == null ? null : NotificationCategory.valueOf(category),
                cursor == null || cursor.isBlank() ? null : cursor,
                size == null ? defaultSize : size,
                since
        );
    }
}
