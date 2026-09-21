package kr.ktb.zura.needu.friend.service;

import java.time.LocalDate;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.friend.dto.response.FriendSummaryResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FriendCursorTest {

    @Test
    void leapDay_encodeAndDecode_treatsBirthdayAsMarchFirst() {
        LocalDate referenceDate = LocalDate.of(2026, 2, 1);
        FriendSummaryResponse friend = new FriendSummaryResponse(
                3L, "윤년생", null, LocalDate.of(2000, 2, 29), false);

        FriendCursor cursor = FriendCursor.decode(FriendCursor.from(referenceDate, friend).encode());

        assertThat(cursor.referenceDate()).isEqualTo(referenceDate);
        assertThat(cursor.birthdayKey()).isEqualTo(301);
        assertThat(cursor.sortKey()).isEqualTo(301);
        assertThat(cursor.userId()).isEqualTo(3L);
    }

    @Test
    void nextYearBirthday_sortKey_movesAfterYearEnd() {
        FriendCursor cursor = new FriendCursor(LocalDate.of(2026, 12, 30), 102, 3L);

        assertThat(cursor.sortKey()).isEqualTo(1302);
    }

    @Test
    void malformedCursor_decode_throwsBadRequest() {
        assertThatThrownBy(() -> FriendCursor.decode("invalid!!"))
                .isInstanceOf(BusinessException.class);
    }
}
