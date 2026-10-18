package kr.ktb.zura.needu.user.dto.response;

import java.util.List;
import kr.ktb.zura.needu.user.type.TasteProfileStatus;

public record TasteProfileResponse(
        TasteProfileStatus analysisStatus,
        List<String> preferenceKeywords,
        List<String> interestKeywords,
        AiSummaryResponse aiSummary
) {

    public static TasteProfileResponse notStarted() {
        return new TasteProfileResponse(
                TasteProfileStatus.NOT_STARTED, List.of(), List.of(), AiSummaryResponse.from(null));
    }
}
