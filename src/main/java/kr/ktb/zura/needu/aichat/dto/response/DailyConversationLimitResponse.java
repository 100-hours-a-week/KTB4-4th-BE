package kr.ktb.zura.needu.aichat.dto.response;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

public record DailyConversationLimitResponse(OffsetDateTime nextConversationAvailableAt) {

    public static DailyConversationLimitResponse from(ZonedDateTime nextConversationAvailableAt) {
        return new DailyConversationLimitResponse(
                nextConversationAvailableAt.withZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime());
    }
}
