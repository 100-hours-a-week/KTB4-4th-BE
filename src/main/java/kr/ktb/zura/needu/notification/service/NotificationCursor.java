package kr.ktb.zura.needu.notification.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kr.ktb.zura.needu.common.cursor.CursorCodec;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.CommonErrorCode;
import kr.ktb.zura.needu.notification.entity.Notification;

public record NotificationCursor(Long id) {

    private static final Pattern CURSOR_PATTERN = Pattern.compile("\\{\"id\":([1-9]\\d*)}");

    public static NotificationCursor from(Notification notification) {
        return new NotificationCursor(notification.getId());
    }

    public static NotificationCursor decode(String cursor) {
        try {
            String decoded = CursorCodec.decode(cursor);
            Matcher matcher = CURSOR_PATTERN.matcher(decoded);
            if (!matcher.matches()) {
                throw invalidCursor();
            }
            return new NotificationCursor(Long.parseLong(matcher.group(1)));
        } catch (IllegalArgumentException exception) {
            throw invalidCursor();
        }
    }

    public String encode() {
        String raw = "{\"id\":" + id + "}";
        return CursorCodec.encode(raw);
    }

    private static BusinessException invalidCursor() {
        return new BusinessException(CommonErrorCode.COMMON_INVALID_REQUEST);
    }
}
