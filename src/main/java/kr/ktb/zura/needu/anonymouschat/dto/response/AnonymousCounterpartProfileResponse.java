package kr.ktb.zura.needu.anonymouschat.dto.response;

import kr.ktb.zura.needu.anonymouschat.type.AnonymousCounterpartType;

// USER면 profileImageUrl(없으면 null = 기본 프로필), ANONYMOUS면 avatar만 채운다
public record AnonymousCounterpartProfileResponse(
        AnonymousCounterpartType type,
        String profileImageUrl,
        AnonymousAvatarResponse avatar
) {

    public static AnonymousCounterpartProfileResponse ofUser(String profileImageUrl) {
        return new AnonymousCounterpartProfileResponse(AnonymousCounterpartType.USER, profileImageUrl, null);
    }

    public static AnonymousCounterpartProfileResponse ofAnonymous(String avatarAnimal, String avatarColor) {
        return new AnonymousCounterpartProfileResponse(
                AnonymousCounterpartType.ANONYMOUS, null, new AnonymousAvatarResponse(avatarAnimal, avatarColor));
    }
}
