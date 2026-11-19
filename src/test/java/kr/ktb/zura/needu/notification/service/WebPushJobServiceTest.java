package kr.ktb.zura.needu.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.List;
import kr.ktb.zura.needu.notification.repository.WebPushJobInsertRow;
import kr.ktb.zura.needu.notification.repository.WebPushJobRepository;
import kr.ktb.zura.needu.notification.type.NotificationType;
import kr.ktb.zura.needu.notification.type.WebPushQueueType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WebPushJobServiceTest {

    private static final int CHUNK_SIZE = 2;
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 11, 14, 0, 0);

    private final WebPushJobRepository webPushJobRepository = mock(WebPushJobRepository.class);

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
        assertThatThrownBy(() -> new WebPushJobService(webPushJobRepository, true, true, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chunk-size");
    }

    private WebPushJobService createService(boolean webPushEnabled, boolean jobEnabled) {
        return new WebPushJobService(webPushJobRepository, webPushEnabled, jobEnabled, CHUNK_SIZE);
    }

    @SuppressWarnings("unchecked")
    private List<WebPushJobInsertRow> captureInsertedRows() {
        ArgumentCaptor<List<WebPushJobInsertRow>> captor = ArgumentCaptor.forClass(List.class);
        verify(webPushJobRepository).insertAll(captor.capture(), eq(CREATED_AT));
        return captor.getValue();
    }
}
