package kr.ktb.zura.needu.anonymouschat.service;

import java.time.Duration;
import java.time.LocalDateTime;
import kr.ktb.zura.needu.anonymouschat.dto.request.CreateAnonymousReportRequest;
import kr.ktb.zura.needu.anonymouschat.dto.response.AnonymousReportResponse;
import kr.ktb.zura.needu.anonymouschat.entity.AnonymousReport;
import kr.ktb.zura.needu.anonymouschat.entity.AnonymousRoom;
import kr.ktb.zura.needu.anonymouschat.exception.AnonymousChatErrorCode;
import kr.ktb.zura.needu.anonymouschat.repository.AnonymousReportRepository;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.user.service.UserBlockService;
import kr.ktb.zura.needu.user.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class AnonymousReportService {

    private final AnonymousChatService anonymousChatService;
    private final AnonymousReportRepository anonymousReportRepository;
    private final UserService userService;
    private final UserBlockService userBlockService;
    private final long blockThreshold;
    private final Duration blockDuration;

    public AnonymousReportService(
            AnonymousChatService anonymousChatService,
            AnonymousReportRepository anonymousReportRepository,
            UserService userService,
            UserBlockService userBlockService,
            @Value("${needu.anonymous-chat.report.block-threshold}") long blockThreshold,
            @Value("${needu.anonymous-chat.report.block-duration}") Duration blockDuration
    ) {
        this.anonymousChatService = anonymousChatService;
        this.anonymousReportRepository = anonymousReportRepository;
        this.userService = userService;
        this.userBlockService = userBlockService;
        this.blockThreshold = blockThreshold;
        this.blockDuration = blockDuration;
    }

    @Transactional
    public AnonymousReportResponse reportRoom(Long userId, Long roomId, CreateAnonymousReportRequest request) {
        userService.validateActiveUser(userId);
        AnonymousRoom room = anonymousChatService.findParticipatingRoom(userId, roomId);
        validateNotDuplicated(userId, roomId);

        AnonymousReport report = saveReport(
                new AnonymousReport(userId, room, request.reason(), request.description()));

        log.info("Anonymous chat report created. reportId={}, roomId={}", report.getId(), roomId);
        blockIfReportThresholdReached(report.getReportedUserId());
        return AnonymousReportResponse.from(report);
    }

    private void validateNotDuplicated(Long userId, Long roomId) {
        if (anonymousReportRepository.existsByReporterUserIdAndRoomId(userId, roomId)) {
            throw new BusinessException(AnonymousChatErrorCode.ANONYMOUS_CHAT_REPORT_DUPLICATED);
        }
    }

    // 같은 신고가 동시에 들어오면 둘 다 중복 확인을 통과할 수 있어 유일 제약 위반도 중복 신고로 처리
    private AnonymousReport saveReport(AnonymousReport report) {
        try {
            return anonymousReportRepository.saveAndFlush(report);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(AnonymousChatErrorCode.ANONYMOUS_CHAT_REPORT_DUPLICATED);
        }
    }

    private void blockIfReportThresholdReached(Long reportedUserId) {
        long reportCount = userService.findLastBlockedAt(reportedUserId)
                .map(lastBlockedAt -> countReportsAfter(reportedUserId, lastBlockedAt))
                .orElseGet(() -> anonymousReportRepository.countByReportedUserId(reportedUserId));
        if (reportCount >= blockThreshold) {
            userBlockService.blockFor(reportedUserId, blockDuration);
        }
    }

    private long countReportsAfter(Long reportedUserId, LocalDateTime lastBlockedAt) {
        return anonymousReportRepository.countByReportedUserIdAndCreatedAtAfter(reportedUserId, lastBlockedAt);
    }
}
