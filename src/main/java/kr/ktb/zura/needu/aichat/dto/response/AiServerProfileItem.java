package kr.ktb.zura.needu.aichat.dto.response;

import java.time.Instant;
import java.util.List;

public record AiServerProfileItem(
        String value,
        Double confidence,
        String linkRole,
        String visibility,
        String intentType,
        Boolean deferralSignal,
        String deferralReason,
        String evidence,
        List<String> taxonomyPath,
        Instant firstSeenAt,
        Instant updatedAt
) {
}
