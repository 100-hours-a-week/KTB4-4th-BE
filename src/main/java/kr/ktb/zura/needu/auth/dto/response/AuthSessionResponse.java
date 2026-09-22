package kr.ktb.zura.needu.auth.dto.response;

import java.time.LocalDate;
import kr.ktb.zura.needu.user.dto.response.UserResponse;

public record AuthSessionResponse(
        SessionUserResponse user,
        LocalDate birthday
) {

    public static AuthSessionResponse from(UserResponse user) {
        return new AuthSessionResponse(SessionUserResponse.from(user), user.birthDate());
    }
}
