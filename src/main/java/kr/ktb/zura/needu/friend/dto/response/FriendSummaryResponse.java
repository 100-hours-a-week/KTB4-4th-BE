package kr.ktb.zura.needu.friend.dto.response;

import java.time.LocalDate;

public record FriendSummaryResponse(
        Long userId,
        String name,
        String profileImageUrl,
        LocalDate birthDate,
        boolean isFavorite
) {
}
