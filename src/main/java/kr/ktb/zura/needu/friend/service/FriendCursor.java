package kr.ktb.zura.needu.friend.service;

import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.MonthDay;
import java.util.Base64;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.CommonErrorCode;
import kr.ktb.zura.needu.friend.dto.response.FriendSummaryResponse;

public record FriendCursor(LocalDate referenceDate, int birthdayKey, Long userId) {

    private static final String DELIMITER = ":";
    private static final int PART_COUNT = 3;
    private static final int LEAP_DAY_KEY = 229;
    private static final int MARCH_FIRST_KEY = 301;
    private static final int NEXT_YEAR_OFFSET = 1200;

    public static FriendCursor from(LocalDate referenceDate, FriendSummaryResponse friend) {
        return new FriendCursor(referenceDate, birthdayKey(friend.birthDate()), friend.userId());
    }

    public static FriendCursor decode(String cursor) {
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = decoded.split(DELIMITER, -1);
            if (parts.length != PART_COUNT) {
                throw invalidCursor();
            }
            LocalDate referenceDate = LocalDate.parse(parts[0]);
            int birthdayKey = Integer.parseInt(parts[1]);
            long userId = Long.parseLong(parts[2]);
            validateBirthdayKey(birthdayKey);
            if (userId <= 0) {
                throw invalidCursor();
            }
            return new FriendCursor(referenceDate, birthdayKey, userId);
        } catch (IllegalArgumentException | DateTimeException exception) {
            throw invalidCursor();
        }
    }

    public String encode() {
        String raw = referenceDate + DELIMITER + birthdayKey + DELIMITER + userId;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public int sortKey() {
        int referenceKey = birthdayKey(referenceDate);
        return birthdayKey >= referenceKey ? birthdayKey : birthdayKey + NEXT_YEAR_OFFSET;
    }

    public static int birthdayKey(LocalDate date) {
        if (date.getMonthValue() == 2 && date.getDayOfMonth() == 29) {
            return MARCH_FIRST_KEY;
        }
        return date.getMonthValue() * 100 + date.getDayOfMonth();
    }

    private static void validateBirthdayKey(int birthdayKey) {
        int month = birthdayKey / 100;
        int day = birthdayKey % 100;
        MonthDay.of(month, day);
        if (birthdayKey == LEAP_DAY_KEY) {
            throw invalidCursor();
        }
    }

    private static BusinessException invalidCursor() {
        return new BusinessException(CommonErrorCode.COMMON_INVALID_REQUEST);
    }
}
