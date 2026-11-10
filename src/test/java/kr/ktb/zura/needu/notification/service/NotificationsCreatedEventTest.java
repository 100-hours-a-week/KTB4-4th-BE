package kr.ktb.zura.needu.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.time.LocalDateTime;
import java.util.List;
import kr.ktb.zura.needu.notification.entity.NotificationEvent;
import kr.ktb.zura.needu.notification.type.NotificationResourceType;
import kr.ktb.zura.needu.notification.type.NotificationType;
import org.junit.jupiter.api.Test;

class NotificationsCreatedEventTest {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 11, 2, 0, 0);

    @Test
    void idsAndReceiversGiven_of_pairsByIndexWithEventValues() {
        NotificationsCreatedEvent createdEvent = NotificationsCreatedEvent.of(
                event(), List.of(11L, 12L), List.of(1L, 2L), CREATED_AT);

        assertThat(createdEvent.notifications())
                .extracting(
                        NotificationsCreatedEvent.CreatedNotification::notificationId,
                        NotificationsCreatedEvent.CreatedNotification::receiverUserId,
                        NotificationsCreatedEvent.CreatedNotification::category,
                        NotificationsCreatedEvent.CreatedNotification::type,
                        NotificationsCreatedEvent.CreatedNotification::title,
                        NotificationsCreatedEvent.CreatedNotification::body,
                        NotificationsCreatedEvent.CreatedNotification::resourceType,
                        NotificationsCreatedEvent.CreatedNotification::resourceId,
                        NotificationsCreatedEvent.CreatedNotification::createdAt
                )
                .containsExactly(
                        tuple(11L, 1L, NotificationType.FRIEND_BIRTHDAY.getCategory(), NotificationType.FRIEND_BIRTHDAY,
                                "알림 제목", "알림 내용", NotificationResourceType.USER, 10L, CREATED_AT),
                        tuple(12L, 2L, NotificationType.FRIEND_BIRTHDAY.getCategory(), NotificationType.FRIEND_BIRTHDAY,
                                "알림 제목", "알림 내용", NotificationResourceType.USER, 10L, CREATED_AT)
                );
    }

    @Test
    void idCountDiffersFromReceivers_of_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> NotificationsCreatedEvent.of(event(), List.of(11L), List.of(1L, 2L), CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private NotificationEvent event() {
        return NotificationEvent.create(
                NotificationType.FRIEND_BIRTHDAY,
                null,
                "알림 제목",
                "알림 내용",
                NotificationResourceType.USER,
                10L,
                "friend-birthday:10:2026-11-09"
        );
    }
}
