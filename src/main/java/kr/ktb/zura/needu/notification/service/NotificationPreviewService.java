package kr.ktb.zura.needu.notification.service;

import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import kr.ktb.zura.needu.notification.entity.Notification;
import kr.ktb.zura.needu.notification.entity.NotificationEvent;
import kr.ktb.zura.needu.notification.type.NotificationResourceType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class NotificationPreviewService {

    private final Map<NotificationResourceType, NotificationPreviewProvider> providers =
            new EnumMap<>(NotificationResourceType.class);

    public NotificationPreviewService(List<NotificationPreviewProvider> providers) {
        providers.forEach(provider -> {
            if (this.providers.putIfAbsent(provider.resourceType(), provider) != null) {
                throw new IllegalStateException(
                        "Duplicate notification preview provider. resourceType=" + provider.resourceType());
            }
        });
    }

    public Map<Long, NotificationPreview> findAllPreviews(Long receiverUserId, List<Notification> notifications) {
        Map<NotificationResourceType, List<Notification>> notificationsByResourceType = notifications.stream()
                .filter(notification -> hasPreviewTarget(notification.getEvent()))
                .collect(Collectors.groupingBy(notification -> notification.getEvent().getResourceType()));

        Map<Long, NotificationPreview> previewsByNotificationId = new HashMap<>();
        notificationsByResourceType.forEach((resourceType, typedNotifications) -> {
            Map<Long, NotificationPreview> previewsByResourceId = findPreviews(
                    receiverUserId,
                    resourceType,
                    typedNotifications.stream().map(notification -> notification.getEvent().getResourceId()).toList());
            typedNotifications.forEach(notification -> {
                NotificationPreview preview = previewsByResourceId.get(notification.getEvent().getResourceId());
                if (preview != null) {
                    previewsByNotificationId.put(notification.getId(), preview);
                }
            });
        });
        return previewsByNotificationId;
    }

    public NotificationPreview findPreview(
            Long receiverUserId, NotificationResourceType resourceType, Long resourceId) {
        if (resourceType == null || resourceId == null || !providers.containsKey(resourceType)) {
            return null;
        }
        return findPreviews(receiverUserId, resourceType, List.of(resourceId)).get(resourceId);
    }

    private boolean hasPreviewTarget(NotificationEvent event) {
        return event.getResourceType() != null
                && event.getResourceId() != null
                && providers.containsKey(event.getResourceType());
    }

    // 미리보기는 부가 정보, 조회에 실패해도 알림 목록과 SSE 전송은 계속
    private Map<Long, NotificationPreview> findPreviews(
            Long receiverUserId, NotificationResourceType resourceType, Collection<Long> resourceIds) {
        try {
            return Objects.requireNonNullElse(
                    providers.get(resourceType).findAllPreviews(receiverUserId, resourceIds), Map.of());
        } catch (RuntimeException exception) {
            log.warn("Notification preview lookup failed. resourceType={}, receiverUserId={}, resourceCount={}",
                    resourceType, receiverUserId, resourceIds.size(), exception);
            return Map.of();
        }
    }
}
