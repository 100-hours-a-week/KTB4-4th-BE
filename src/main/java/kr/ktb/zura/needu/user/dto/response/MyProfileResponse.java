package kr.ktb.zura.needu.user.dto.response;

import java.time.LocalDate;
import kr.ktb.zura.needu.user.entity.User;
import kr.ktb.zura.needu.user.type.AccountLinkStatus;
import kr.ktb.zura.needu.user.type.AccountProvider;

public record MyProfileResponse(
        Long id,
        String name,
        String profileImageUrl,
        LocalDate birthDate,
        LinkedAccountResponse linkedAccount
) {

    public static MyProfileResponse from(User user) {
        return new MyProfileResponse(
                user.getId(),
                user.getNickname(),
                user.getProfileImageUrl(),
                user.getBirthDate(),
                new LinkedAccountResponse(AccountProvider.KAKAO, AccountLinkStatus.CONNECTED)
        );
    }
}
