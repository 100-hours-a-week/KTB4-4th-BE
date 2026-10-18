package kr.ktb.zura.needu.user.dto.response;

import kr.ktb.zura.needu.user.type.AiSummaryStatus;

public record AiSummaryResponse(AiSummaryStatus status, String content) {

    public static AiSummaryResponse from(String content) {
        return content == null || content.isBlank()
                ? new AiSummaryResponse(AiSummaryStatus.NOT_READY, null)
                : new AiSummaryResponse(AiSummaryStatus.READY, content);
    }
}
