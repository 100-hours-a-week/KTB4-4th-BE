package kr.ktb.zura.needu.friend.dto.response;

import java.time.LocalDate;

/**
 * 생일 알림 배치 1회 실행 결과
 *
 * @param targetCount 대상 조회 결과 행 수(친구 관계 수)
 */
public record FriendBirthdayNotificationResult(
        LocalDate executionDate,
        LocalDate birthdayDate,
        int birthdayFriendCount,
        int targetCount,
        int createdNotificationCount
) {
}
