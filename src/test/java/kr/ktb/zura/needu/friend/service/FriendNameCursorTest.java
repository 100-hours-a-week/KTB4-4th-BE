package kr.ktb.zura.needu.friend.service;

import kr.ktb.zura.needu.friend.dto.response.FriendSummaryResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FriendNameCursorTest {

    @Test
    void nameContainsDelimiter_encodeAndDecode_preservesName() {
        FriendSummaryResponse friend = new FriendSummaryResponse(3L, "가나:다라", null, null, false);

        FriendNameCursor cursor = FriendNameCursor.decode(FriendNameCursor.from(friend).encode());

        assertThat(cursor).isEqualTo(new FriendNameCursor("가나:다라", 3L));
    }
}
