package kr.ktb.zura.needu.friend.service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.CommonErrorCode;
import kr.ktb.zura.needu.friend.dto.response.FriendSummaryResponse;

public record FriendNameCursor(String name, Long userId) {

    private static final String DELIMITER = ":";

    public static FriendNameCursor from(FriendSummaryResponse friend) {
        return new FriendNameCursor(friend.name(), friend.userId());
    }

    public static FriendNameCursor decode(String cursor) {
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = decoded.split(DELIMITER, 2);
            long userId = Long.parseLong(parts[0]);
            if (parts.length != 2 || userId <= 0) {
                throw invalidCursor();
            }
            return new FriendNameCursor(parts[1], userId);
        } catch (IllegalArgumentException | ArrayIndexOutOfBoundsException exception) {
            throw invalidCursor();
        }
    }

    public String encode() {
        String raw = userId + DELIMITER + name;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static BusinessException invalidCursor() {
        return new BusinessException(CommonErrorCode.COMMON_INVALID_REQUEST);
    }
}
