package kr.ktb.zura.needu.friend.dto.response;

import java.time.LocalDate;
import java.util.Map;
import kr.ktb.zura.needu.notification.service.NotificationStage;

/**
 * 생일 알림 배치 1회 실행 결과
 *
 * @param targetCount 대상 조회 결과 행 수(친구 관계 수)
 * @param stageMillis 알림 생성·발행 단계별 누적 시간
 * @param commitAndOtherMillis 생성 시간 중 단계로 나누지 않은 나머지. 트랜잭션 커밋이 대부분이다
 */
public record FriendBirthdayNotificationResult(
        LocalDate executionDate,
        LocalDate birthdayDate,
        int birthdayFriendCount,
        int targetCount,
        int createdNotificationCount,
        long totalMillis,
        long targetQueryMillis,
        long createMillis,
        Map<NotificationStage, Long> stageMillis,
        long commitAndOtherMillis
) {
}
