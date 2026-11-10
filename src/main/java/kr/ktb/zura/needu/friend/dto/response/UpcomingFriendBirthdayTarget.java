package kr.ktb.zura.needu.friend.dto.response;

import java.time.LocalDate;

public record UpcomingFriendBirthdayTarget(
        Long userId,
        String nickname,
        String profileImageUrl,
        LocalDate birthDate
) {
}
