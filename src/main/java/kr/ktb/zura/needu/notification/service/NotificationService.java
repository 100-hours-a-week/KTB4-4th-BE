package kr.ktb.zura.needu.notification.service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import kr.ktb.zura.needu.common.response.CursorPageResponse;
import kr.ktb.zura.needu.notification.dto.request.NotificationSearchCondition;
import kr.ktb.zura.needu.notification.dto.response.NotificationListResponse;
import kr.ktb.zura.needu.notification.dto.response.NotificationReadResponse;
import kr.ktb.zura.needu.notification.dto.response.NotificationResponse;
import kr.ktb.zura.needu.notification.dto.response.NotificationSummaryResponse;
import kr.ktb.zura.needu.notification.entity.Notification;
import kr.ktb.zura.needu.notification.repository.NotificationRepository;
import kr.ktb.zura.needu.notification.type.NotificationCategory;
import kr.ktb.zura.needu.notification.type.NotificationType;
import kr.ktb.zura.needu.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserService userService;

    public NotificationSummaryResponse findNotificationSummary(Long userId) {
        userService.validateActiveUser(userId);
        long unreadCount = notificationRepository.countUnreadByReceiverUserId(userId);
        return new NotificationSummaryResponse(unreadCount > 0, unreadCount);
    }

    public NotificationListResponse findAllNotifications(Long userId, NotificationSearchCondition condition) {
        userService.validateActiveUser(userId);
        List<NotificationType> types = findTypes(condition.category());
        Long cursorId = condition.cursor() == null ? null : NotificationCursor.decode(condition.cursor()).id();
        List<Notification> notifications = notificationRepository.findAllVisibleByReceiverUserId(
                userId, types, condition.since(), cursorId, Limit.of(condition.size() + 1));

        boolean hasNext = notifications.size() > condition.size();
        List<Notification> pageItems = hasNext ? notifications.subList(0, condition.size()) : notifications;
        String nextCursor = hasNext ? NotificationCursor.from(pageItems.getLast()).encode() : null;
        List<NotificationResponse> items = pageItems.stream()
                .map(notification -> NotificationResponse.from(notification, isTargetAvailable(notification)))
                .toList();
        long unreadCount = notificationRepository.countUnreadByReceiverUserId(userId);
        long newCount = condition.since() == null
                ? 0
                : notificationRepository.countNewByReceiverUserId(userId, types, condition.since());

        return new NotificationListResponse(
                new CursorPageResponse<>(items, nextCursor, hasNext),
                unreadCount,
                newCount,
                LocalDateTime.now(ZoneOffset.UTC)
        );
    }

    private List<NotificationType> findTypes(NotificationCategory category) {
        if (category == null) {
            return List.of(NotificationType.values());
        }
        return Arrays.stream(NotificationType.values())
                .filter(type -> type.getCategory() == category)
                .toList();
    }

    private boolean isTargetAvailable(Notification notification) {
        // shortcut: 대상 도메인별 존재 확인 API가 준비되기 전까지 식별자가 있으면 접근 가능한 대상으로 본다.
        return notification.getEvent().getResourceType() != null && notification.getEvent().getResourceId() != null;
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
