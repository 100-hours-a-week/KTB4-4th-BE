package kr.ktb.zura.needu.anonymouschat.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import kr.ktb.zura.needu.anonymouschat.type.AnonymousChatRole;

public record AnonymousRoomSearchCondition(
        @NotNull AnonymousChatRole role,
        String cursor,
        @NotNull @Min(1) @Max(50) Integer size
) {
}
