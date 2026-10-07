package kr.ktb.zura.needu.notification.service;

import kr.ktb.zura.needu.notification.dto.request.NotificationSearchCondition;
import kr.ktb.zura.needu.notification.dto.response.NotificationListResponse;
import kr.ktb.zura.needu.notification.dto.response.NotificationReadResponse;
import kr.ktb.zura.needu.notification.dto.response.NotificationSummaryResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class NotificationService {

    // TODO: notifications 테이블을 추가한 뒤 미읽음 개수를 조회
    //  미읽음 알림 존재 여부 확인 API
    public NotificationSummaryResponse findNotificationSummary(Long userId) {
        throw new UnsupportedOperationException("알림 존재 여부 확인 로직 미구현");
    }

    // TODO: 최신순 커서 조회, category 필터, since 이후 개수(newCount), 이동 대상 존재 여부(targetAvailable)를 구현
    //  알림 목록 조회 API
    public NotificationListResponse findAllNotifications(Long userId, NotificationSearchCondition condition) {
        throw new UnsupportedOperationException("알림 목록 조회 로직 미구현");
    }

    // TODO: 내 알림만 조회(findByIdAndReceiverUserId)하고, 이미 읽은 알림은 처음 readAt을 유지(멱등)
    //  알림 읽음 처리 API
    @Transactional
    public NotificationReadResponse readNotification(Long userId, Long notificationId) {
        throw new UnsupportedOperationException("알림 읽음 처리 로직 미구현");
    }

    // TODO: 내 알림이 아니거나 이미 삭제됐으면 NOTIFICATION_NOT_FOUND
    //  알림 삭제 API
    @Transactional
    public void deleteNotification(Long userId, Long notificationId) {
        throw new UnsupportedOperationException("알림 삭제 로직 미구현");
    }
}
