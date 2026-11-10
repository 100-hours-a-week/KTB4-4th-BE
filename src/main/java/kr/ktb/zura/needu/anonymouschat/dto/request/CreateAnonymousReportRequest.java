package kr.ktb.zura.needu.anonymouschat.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kr.ktb.zura.needu.anonymouschat.entity.AnonymousReport;
import kr.ktb.zura.needu.anonymouschat.type.AnonymousReportReason;

public record CreateAnonymousReportRequest(
        @NotNull AnonymousReportReason reason,
        @Size(max = AnonymousReport.DESCRIPTION_MAX_LENGTH) String description
) {
}
