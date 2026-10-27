package kr.ktb.zura.needu.friend.dto.response;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import kr.ktb.zura.needu.friend.type.FriendEventType;

public record UpcomingFriendEventResponse(
        FriendEventType eventType,
        LocalDate eventDate,
        int daysRemaining,
        UpcomingFriendResponse friend
) {

    public static UpcomingFriendEventResponse from(
            UpcomingFriendBirthdayTarget target,
            LocalDate eventDate,
            LocalDate today
    ) {
        return new UpcomingFriendEventResponse(
                FriendEventType.BIRTHDAY,
                eventDate,
                Math.toIntExact(ChronoUnit.DAYS.between(today, eventDate)),
                UpcomingFriendResponse.from(target)
        );
    }
}
