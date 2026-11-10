package kr.ktb.zura.needu.anonymouschat.dto.response;

import java.time.OffsetDateTime;
import kr.ktb.zura.needu.anonymouschat.entity.AnonymousReport;
import kr.ktb.zura.needu.anonymouschat.support.AnonymousChatTime;

public record AnonymousReportResponse(Long reportId, OffsetDateTime createdAt) {

    public static AnonymousReportResponse from(AnonymousReport report) {
        return new AnonymousReportResponse(report.getId(), AnonymousChatTime.toKst(report.getCreatedAt()));
    }
}
