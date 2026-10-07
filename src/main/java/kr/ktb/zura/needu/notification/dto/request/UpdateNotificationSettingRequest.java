package kr.ktb.zura.needu.notification.dto.request;

import jakarta.validation.constraints.AssertTrue;

public record UpdateNotificationSettingRequest(
        Boolean friendJoined,
        Boolean friendBirthday,
        Boolean anniversaryEvent,
        Boolean marketing
) {

    @AssertTrue
    public boolean isAnyFieldPresent() {
        return friendJoined != null || friendBirthday != null || anniversaryEvent != null || marketing != null;
    }
}
