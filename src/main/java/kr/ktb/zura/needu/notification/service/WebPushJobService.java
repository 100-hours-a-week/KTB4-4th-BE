package kr.ktb.zura.needu.notification.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import kr.ktb.zura.needu.notification.entity.NotificationEvent;
import kr.ktb.zura.needu.notification.entity.WebPushJob;
import kr.ktb.zura.needu.notification.repository.NotificationEventRepository;
import kr.ktb.zura.needu.notification.repository.NotificationReceiver;
import kr.ktb.zura.needu.notification.repository.NotificationRepository;
import kr.ktb.zura.needu.notification.repository.WebPushJobInsertRow;
import kr.ktb.zura.needu.notification.repository.WebPushJobRepository;
import kr.ktb.zura.needu.notification.type.WebPushQueueType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class WebPushJobService {

    private final WebPushJobRepository webPushJobRepository;
    private final NotificationEventRepository notificationEventRepository;
    private final NotificationRepository notificationRepository;
    private final Clock clock;
    private final boolean enabled;
    private final int chunkSize;
    private final Duration leaseDuration;

    public WebPushJobService(
            WebPushJobRepository webPushJobRepository,
            NotificationEventRepository notificationEventRepository,
            NotificationRepository notificationRepository,
            Clock clock,
            @Value("${notification.webpush.enabled:false}") boolean webPushEnabled,
            @Value("${needu.notification.webpush-job.enabled}") boolean jobEnabled,
            @Value("${needu.notification.webpush-job.chunk-size}") int chunkSize,
            @Value("${needu.notification.webpush-job.lease-duration}") Duration leaseDuration
    ) {
        // 0 이하면 chunk 반복이 끝나지 않으므로 기동 시점에 막는다
        if (chunkSize <= 0) {
            throw new IllegalArgumentException(
                    "needu.notification.webpush-job.chunk-size must be positive. value=" + chunkSize);
        }
        if (leaseDuration.isZero() || leaseDuration.isNegative()) {
            throw new IllegalArgumentException(
                    "needu.notification.webpush-job.lease-duration must be positive. value=" + leaseDuration);
        }
        this.webPushJobRepository = webPushJobRepository;
        this.notificationEventRepository = notificationEventRepository;
        this.notificationRepository = notificationRepository;
        this.clock = clock;
        this.enabled = webPushEnabled && jobEnabled;
        this.chunkSize = chunkSize;
        this.leaseDuration = leaseDuration;
    }

    /**
     * 알림 이벤트별 수신자를 chunk로 나눠 웹푸시 발송 작업을 만든다.
     * 알림을 저장한 트랜잭션 안에서만 호출해, 커밋된 알림만 빠짐없이 발송 대상이 되게 한다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void createJobs(List<WebPushJobTarget> targets, LocalDateTime createdAt) {
        if (!enabled) {
            return;
        }

        List<WebPushJobInsertRow> rows = new ArrayList<>();
        for (WebPushJobTarget target : targets) {
            List<Long> notificationIds = target.notificationIds();
            for (int fromIndex = 0; fromIndex < notificationIds.size(); fromIndex += chunkSize) {
                int toIndex = Math.min(fromIndex + chunkSize, notificationIds.size());
                rows.add(new WebPushJobInsertRow(
                        target.notificationEventId(),
                        target.type().getWebPushQueueType(),
                        notificationIds.subList(fromIndex, toIndex)
                ));
            }
        }
        webPushJobRepository.insertAll(rows, createdAt);
    }


    @Transactional
    public List<WebPushJob> takeJobs(WebPushQueueType queueType, int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("Web push job take limit must be positive. limit=" + limit);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        List<Long> ids = webPushJobRepository.findAllAvailableIdsForUpdate(queueType, now, limit);
        if (ids.isEmpty()) {
            return List.of();
        }
        webPushJobRepository.updateToRunning(ids, UUID.randomUUID().toString(), now.plus(leaseDuration), now);
        return webPushJobRepository.findAllById(ids);
    }


    @Transactional
    public void completeJob(WebPushJob job) {
        int deleted = webPushJobRepository.deleteByIdAndLeaseToken(job.getId(), job.getLeaseToken());
        if (deleted == 0) {
            log.info("Web push job lease lost before completion. jobId={}", job.getId());
        }
    }


    @Transactional(readOnly = true)
    public List<NotificationsCreatedEvent.CreatedNotification> findDeliveryNotifications(WebPushJob job) {
        NotificationEvent event = notificationEventRepository.findById(job.getNotificationEventId()).orElse(null);
        if (event == null) {
            return List.of();
        }

        Map<Long, Long> receiverUserIdByNotificationId = notificationRepository
                .findAllActiveReceiversByIdIn(job.getNotificationIds())
                .stream()
                .collect(Collectors.toMap(
                        NotificationReceiver::getNotificationId,
                        NotificationReceiver::getReceiverUserId
                ));
        List<Long> notificationIds = job.getNotificationIds().stream()
                .filter(receiverUserIdByNotificationId::containsKey)
                .toList();
        List<Long> receiverUserIds = notificationIds.stream()
                .map(receiverUserIdByNotificationId::get)
                .toList();
        return NotificationsCreatedEvent.of(event, notificationIds, receiverUserIds, job.getCreatedAt())
                .notifications();
    }
}
