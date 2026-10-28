package kr.ktb.zura.needu.anonymouschat.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateAnonymousRoomRequest(
        @NotNull @Positive Long recipientUserId,
        @NotNull @Positive Long productId
) {
}
