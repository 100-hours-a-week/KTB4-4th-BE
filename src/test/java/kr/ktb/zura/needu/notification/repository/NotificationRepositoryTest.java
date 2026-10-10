package kr.ktb.zura.needu.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import kr.ktb.zura.needu.notification.entity.Notification;
import kr.ktb.zura.needu.notification.entity.NotificationEvent;
import kr.ktb.zura.needu.notification.type.NotificationResourceType;
import kr.ktb.zura.needu.notification.type.NotificationType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Limit;

@DataJpaTest
class NotificationRepositoryTest {

    private static final Long USER_ID = 1L;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationEventRepository notificationEventRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void notificationsHaveDifferentStates_countUnreadByReceiverUserId_countsOnlyActiveUnreadNotifications() {
        LocalDateTime now = LocalDateTime.now();
        Notification unread = createNotification(USER_ID, "unread", now.plusDays(1));
        Notification otherUserUnread = createNotification(2L, "other-user", now.plusDays(1));
        Notification read = createNotification(USER_ID, "read", now.plusDays(1));
        Notification deleted = createNotification(USER_ID, "deleted", now.plusDays(1));
        Notification expired = createNotification(USER_ID, "expired", now.minusDays(1));
        read.read();
        deleted.delete();
        notificationRepository.saveAllAndFlush(List.of(unread, otherUserUnread, read, deleted, expired));

        long unreadCount = notificationRepository.countUnreadByReceiverUserId(USER_ID);

        assertThat(unreadCount).isEqualTo(1);
    }

    @Test
    void multipleUsersHaveUnreadNotifications_findAllUnreadCounts_returnsCountsByUser() {
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(1);
        notificationRepository.saveAllAndFlush(List.of(
                createNotification(1L, "user-1-first", expiresAt),
                createNotification(1L, "user-1-second", expiresAt),
                createNotification(2L, "user-2", expiresAt),
                createNotification(3L, "excluded-user", expiresAt)
        ));

        assertThat(notificationRepository.findAllUnreadCountsByReceiverUserIdIn(List.of(1L, 2L)))
                .extracting(
                        NotificationUnreadCount::getReceiverUserId,
                        NotificationUnreadCount::getUnreadCount
                )
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(1L, 2L),
                        org.assertj.core.groups.Tuple.tuple(2L, 1L)
                );
    }

    @Test
    void conditionsGiven_findAllVisibleByReceiverUserId_filtersAndOrdersNotifications() {
        LocalDateTime now = LocalDateTime.now();
        Notification olderPoke = createNotification(USER_ID, "older-poke", now.plusDays(1), NotificationType.POKE);
        Notification newerPoke = createNotification(USER_ID, "newer-poke", now.plusDays(1), NotificationType.POKE);
        Notification event = createNotification(
                USER_ID, "event", now.plusDays(1), NotificationType.FRIEND_BIRTHDAY);
        Notification otherUser = createNotification(2L, "other", now.plusDays(1), NotificationType.POKE);
        notificationRepository.saveAllAndFlush(List.of(olderPoke, newerPoke, event, otherUser));

        List<Notification> firstPage = notificationRepository.findAllVisibleByReceiverUserId(
                USER_ID, List.of(NotificationType.POKE), now.minusDays(1), null, Limit.of(2));
        List<Notification> afterCursor = notificationRepository.findAllVisibleByReceiverUserId(
                USER_ID, List.of(NotificationType.POKE), now.minusDays(1), newerPoke.getId(), Limit.of(2));
        List<Notification> afterFutureSince = notificationRepository.findAllVisibleByReceiverUserId(
                USER_ID, List.of(NotificationType.POKE), now.plusDays(1), null, Limit.of(2));

        assertThat(firstPage).extracting(Notification::getId)
                .containsExactly(newerPoke.getId(), olderPoke.getId());
        assertThat(afterCursor).extracting(Notification::getId).containsExactly(olderPoke.getId());
        assertThat(afterFutureSince).isEmpty();
        assertThat(notificationRepository.countNewByReceiverUserId(
                USER_ID, List.of(NotificationType.POKE), now.minusDays(1))).isEqualTo(2);
    }

    @Test
    void notificationExists_findOneById_returnsNotificationWithEvent() {
        Notification notification = notificationRepository.saveAndFlush(
                createNotification(USER_ID, "owned-notification", LocalDateTime.now().plusDays(1)));

        assertThat(notificationRepository.findOneById(notification.getId()))
                .get()
                .satisfies(found -> {
                    assertThat(found.getReceiverUserId()).isEqualTo(USER_ID);
                    assertThat(found.getEvent().getType()).isEqualTo(NotificationType.POKE);
                });
    }

    @Test
    void notificationDeleted_flush_persistsDeletedAtAndExcludesUnreadCount() {
        Notification notification = notificationRepository.saveAndFlush(
                createNotification(USER_ID, "soft-delete", LocalDateTime.now().plusDays(1)));

        notification.delete();
        notificationRepository.flush();
        entityManager.clear();

        assertThat(notificationRepository.findOneById(notification.getId()))
                .get()
                .extracting(Notification::getDeletedAt)
                .isNotNull();
        assertThat(notificationRepository.countUnreadByReceiverUserId(USER_ID)).isZero();
    }

    private Notification createNotification(Long receiverUserId, String dedupKey, LocalDateTime expiresAt) {
        return createNotification(receiverUserId, dedupKey, expiresAt, NotificationType.POKE);
    }

    private Notification createNotification(
            Long receiverUserId,
            String dedupKey,
            LocalDateTime expiresAt,
            NotificationType type
    ) {
        NotificationEvent event = notificationEventRepository.save(NotificationEvent.create(
                type,
                10L,
                "알림 제목",
                "알림 내용",
                NotificationResourceType.USER,
                receiverUserId,
                dedupKey
        ));
        return Notification.create(event, receiverUserId, expiresAt);
    }
}
