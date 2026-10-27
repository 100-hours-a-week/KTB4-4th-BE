package kr.ktb.zura.needu.anonymouschat.dto.response;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import kr.ktb.zura.needu.anonymouschat.entity.AnonymousReport;

public record AnonymousReportResponse(Long reportId, OffsetDateTime createdAt) {

    public static AnonymousReportResponse from(AnonymousReport report) {
        return new AnonymousReportResponse(report.getId(), report.getCreatedAt().atOffset(ZoneOffset.UTC));
    }
}
