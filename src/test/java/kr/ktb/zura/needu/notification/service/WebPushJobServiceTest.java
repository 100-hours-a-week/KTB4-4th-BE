package kr.ktb.zura.needu.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import kr.ktb.zura.needu.notification.entity.NotificationEvent;
import kr.ktb.zura.needu.notification.entity.WebPushJob;
import kr.ktb.zura.needu.notification.repository.NotificationEventRepository;
import kr.ktb.zura.needu.notification.repository.NotificationReceiver;
import kr.ktb.zura.needu.notification.repository.NotificationRepository;
import kr.ktb.zura.needu.notification.repository.WebPushJobInsertRow;
import kr.ktb.zura.needu.notification.repository.WebPushJobRepository;
import kr.ktb.zura.needu.notification.type.NotificationResourceType;
import kr.ktb.zura.needu.notification.type.NotificationType;
import kr.ktb.zura.needu.notification.type.WebPushQueueType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class WebPushJobServiceTest {

    private static final int CHUNK_SIZE = 2;
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 11, 14, 0, 0);

    private final WebPushJobRepository webPushJobRepository = mock(WebPushJobRepository.class);
    private final NotificationEventRepository notificationEventRepository = mock(NotificationEventRepository.class);
    private final NotificationRepository notificationRepository = mock(NotificationRepository.class);

    @Test
    void receiversExceedChunkSize_createJobs_insertsJobsByChunkKeepingOrder() {
        WebPushJobService service = createService(true, true);

        service.createJobs(List.of(
                new WebPushJobTarget(100L, NotificationType.FRIEND_BIRTHDAY, List.of(1L, 2L, 3L, 4L, 5L))
        ), CREATED_AT);

        assertThat(captureInsertedRows())
                .extracting(
                        WebPushJobInsertRow::notificationEventId,
                        WebPushJobInsertRow::queueType,
                        WebPushJobInsertRow::notificationIds
                )
                .containsExactly(
                        tuple(100L, WebPushQueueType.BULK, List.of(1L, 2L)),
                        tuple(100L, WebPushQueueType.BULK, List.of(3L, 4L)),
                        tuple(100L, WebPushQueueType.BULK, List.of(5L))
                );
    }

    @Test
    void multipleTargets_createJobs_insertsOnceWithQueueTypeOfEachNotificationType() {
        WebPushJobService service = createService(true, true);

        service.createJobs(List.of(
                new WebPushJobTarget(100L, NotificationType.POKE, List.of(1L)),
                new WebPushJobTarget(101L, NotificationType.FRIEND_BIRTHDAY, List.of(2L, 3L))
        ), CREATED_AT);

        assertThat(captureInsertedRows())
                .extracting(WebPushJobInsertRow::notificationEventId, WebPushJobInsertRow::queueType)
                .containsExactly(
                        tuple(100L, WebPushQueueType.REALTIME),
                        tuple(101L, WebPushQueueType.BULK)
                );
    }

    @Test
    void jobQueueDisabled_createJobs_doesNotInsert() {
        WebPushJobService service = createService(true, false);

        service.createJobs(List.of(new WebPushJobTarget(100L, NotificationType.POKE, List.of(1L))), CREATED_AT);

        verify(webPushJobRepository, never()).insertAll(anyList(), any());
    }

    @Test
    void webPushDisabled_createJobs_doesNotInsert() {
        WebPushJobService service = createService(false, true);

        service.createJobs(List.of(new WebPushJobTarget(100L, NotificationType.POKE, List.of(1L))), CREATED_AT);

        verify(webPushJobRepository, never()).insertAll(anyList(), any());
    }

    @Test
    void nonPositiveChunkSize_create_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> new WebPushJobService(
                webPushJobRepository, notificationEventRepository, notificationRepository, true, true, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chunk-size");
    }

    @Test
    void someNotificationsDeleted_findDeliveryNotifications_returnsRemainingInJobOrderWithEventContent() {
        WebPushJob job = job(100L, List.of(3L, 1L, 2L));
        given(notificationEventRepository.findById(100L)).willReturn(Optional.of(event(100L)));
        given(notificationRepository.findAllActiveReceiversByIdIn(List.of(3L, 1L, 2L)))
                .willReturn(List.of(new Receiver(1L, 11L), new Receiver(3L, 33L)));

        List<NotificationsCreatedEvent.CreatedNotification> notifications =
                createService(true, true).findDeliveryNotifications(job);

        assertThat(notifications)
                .extracting(
                        NotificationsCreatedEvent.CreatedNotification::notificationId,
                        NotificationsCreatedEvent.CreatedNotification::receiverUserId,
                        NotificationsCreatedEvent.CreatedNotification::type,
                        NotificationsCreatedEvent.CreatedNotification::title,
                        NotificationsCreatedEvent.CreatedNotification::createdAt
                )
                .containsExactly(
                        tuple(3L, 33L, NotificationType.POKE, "친구가 콕 찔렀어요.", CREATED_AT),
                        tuple(1L, 11L, NotificationType.POKE, "친구가 콕 찔렀어요.", CREATED_AT)
                );
    }

    @Test
    void eventDeleted_findDeliveryNotifications_returnsEmptyWithoutReadingReceivers() {
        WebPushJob job = job(100L, List.of(1L));
        given(notificationEventRepository.findById(100L)).willReturn(Optional.empty());

        assertThat(createService(true, true).findDeliveryNotifications(job)).isEmpty();
        verify(notificationRepository, never()).findAllActiveReceiversByIdIn(any());
    }

    private WebPushJob job(Long notificationEventId, List<Long> notificationIds) {
        WebPushJob job = WebPushJob.create(notificationEventId, WebPushQueueType.REALTIME, notificationIds, CREATED_AT);
        ReflectionTestUtils.setField(job, "createdAt", CREATED_AT);
        return job;
    }

    private NotificationEvent event(Long eventId) {
        NotificationEvent event = NotificationEvent.create(
                NotificationType.POKE,
                2L,
                "친구가 콕 찔렀어요.",
                "친구의 알림을 확인해 보세요.",
                NotificationResourceType.USER,
                2L,
                null
        );
        ReflectionTestUtils.setField(event, "id", eventId);
        return event;
    }

    private record Receiver(Long notificationId, Long receiverUserId) implements NotificationReceiver {

        @Override
        public Long getNotificationId() {
            return notificationId;
        }

        @Override
        public Long getReceiverUserId() {
            return receiverUserId;
        }
    }

    private WebPushJobService createService(boolean webPushEnabled, boolean jobEnabled) {
        return new WebPushJobService(webPushJobRepository, notificationEventRepository, notificationRepository,
                webPushEnabled, jobEnabled, CHUNK_SIZE);
    }

    @SuppressWarnings("unchecked")
    private List<WebPushJobInsertRow> captureInsertedRows() {
        ArgumentCaptor<List<WebPushJobInsertRow>> captor = ArgumentCaptor.forClass(List.class);
        verify(webPushJobRepository).insertAll(captor.capture(), eq(CREATED_AT));
        return captor.getValue();
    }
}
