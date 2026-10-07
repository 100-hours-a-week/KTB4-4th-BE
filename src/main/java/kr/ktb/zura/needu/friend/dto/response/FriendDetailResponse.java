package kr.ktb.zura.needu.friend.dto.response;

import java.time.LocalDate;
import java.util.List;
import kr.ktb.zura.needu.user.dto.response.TasteProfileResponse;
import kr.ktb.zura.needu.user.dto.response.UserDetailResponse;

public record FriendDetailResponse(
        Long userId,
        String name,
        String profileImageUrl,
        LocalDate birthDate,
        boolean tasteAnalysisCompleted,
        List<String> tasteKeywords,
        List<String> interestKeywords,
        String interestSummary,
        boolean isFavorite
) {

    public static FriendDetailResponse of(
            UserDetailResponse user, TasteProfileResponse tasteProfile, boolean isFavorite) {
        return new FriendDetailResponse(
                user.id(),
                user.nickname(),
                user.profileImageUrl(),
                user.birthDate(),
                user.tasteAnalysisCompleted(),
                tasteProfile.preferenceKeywords(),
                tasteProfile.interestKeywords(),
                tasteProfile.aiSummary().content(),
                isFavorite
        );
    }
}
