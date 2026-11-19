package kr.ktb.zura.needu.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 15, 0, 0);
    private static final Clock CLOCK = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    private static final Duration LEASE_DURATION = Duration.ofMinutes(5);

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
        assertThatThrownBy(() -> createService(true, true, 0, LEASE_DURATION))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chunk-size");
    }

    @Test
    void nonPositiveLeaseDuration_create_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> createService(true, true, CHUNK_SIZE, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lease-duration");
    }

    @Test
    void availableJobs_takeJobs_leasesTakenJobsWithOneTokenAndReturnsThem() {
        List<WebPushJob> jobs = List.of(job(100L, List.of(1L)), job(101L, List.of(2L)));
        given(webPushJobRepository.findAllAvailableIdsForUpdate(WebPushQueueType.REALTIME, NOW, 10))
                .willReturn(List.of(7L, 8L));
        given(webPushJobRepository.findAllById(List.of(7L, 8L))).willReturn(jobs);

        List<WebPushJob> taken = createService(true, true).takeJobs(WebPushQueueType.REALTIME, 10);

        assertThat(taken).isEqualTo(jobs);
        ArgumentCaptor<String> leaseTokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(webPushJobRepository).updateToRunning(
                eq(List.of(7L, 8L)), leaseTokenCaptor.capture(), eq(NOW.plus(LEASE_DURATION)), eq(NOW));
        assertThat(leaseTokenCaptor.getValue()).isNotBlank();
    }

    @Test
    void noAvailableJobs_takeJobs_returnsEmptyWithoutLeasing() {
        given(webPushJobRepository.findAllAvailableIdsForUpdate(WebPushQueueType.BULK, NOW, 10))
                .willReturn(List.of());

        assertThat(createService(true, true).takeJobs(WebPushQueueType.BULK, 10)).isEmpty();
        verify(webPushJobRepository, never()).updateToRunning(any(), any(), any(), any());
        verify(webPushJobRepository, never()).findAllById(any());
    }

    @Test
    void nonPositiveLimit_takeJobs_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> createService(true, true).takeJobs(WebPushQueueType.REALTIME, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void leasedJob_completeJob_deletesWithLeaseToken() {
        WebPushJob job = job(100L, List.of(1L));
        ReflectionTestUtils.setField(job, "id", 7L);
        ReflectionTestUtils.setField(job, "leaseToken", "lease-token");
        given(webPushJobRepository.deleteByIdAndLeaseToken(7L, "lease-token")).willReturn(1);

        createService(true, true).completeJob(job);

        verify(webPushJobRepository).deleteByIdAndLeaseToken(7L, "lease-token");
    }

    @Test
    void leaseLost_completeJob_doesNotThrow() {
        WebPushJob job = job(100L, List.of(1L));
        ReflectionTestUtils.setField(job, "id", 7L);
        ReflectionTestUtils.setField(job, "leaseToken", "old-lease-token");
        given(webPushJobRepository.deleteByIdAndLeaseToken(7L, "old-lease-token")).willReturn(0);

        assertThatCode(() -> createService(true, true).completeJob(job)).doesNotThrowAnyException();
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
        return createService(webPushEnabled, jobEnabled, CHUNK_SIZE, LEASE_DURATION);
    }

    private WebPushJobService createService(
            boolean webPushEnabled,
            boolean jobEnabled,
            int chunkSize,
            Duration leaseDuration
    ) {
        return new WebPushJobService(webPushJobRepository, notificationEventRepository, notificationRepository,
                CLOCK, webPushEnabled, jobEnabled, chunkSize, leaseDuration);
    }

    @SuppressWarnings("unchecked")
    private List<WebPushJobInsertRow> captureInsertedRows() {
        ArgumentCaptor<List<WebPushJobInsertRow>> captor = ArgumentCaptor.forClass(List.class);
        verify(webPushJobRepository).insertAll(captor.capture(), eq(CREATED_AT));
        return captor.getValue();
    }
}
