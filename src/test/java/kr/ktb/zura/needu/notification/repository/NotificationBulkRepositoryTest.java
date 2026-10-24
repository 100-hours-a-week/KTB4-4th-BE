package kr.ktb.zura.needu.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import kr.ktb.zura.needu.notification.entity.Notification;
import kr.ktb.zura.needu.notification.entity.NotificationEvent;
import kr.ktb.zura.needu.notification.repository.NotificationBulkRepository.NotificationInsertRow;
import kr.ktb.zura.needu.notification.type.NotificationResourceType;
import kr.ktb.zura.needu.notification.type.NotificationType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(NotificationBulkRepository.class)
class NotificationBulkRepositoryTest {

    @Autowired
    private NotificationBulkRepository notificationBulkRepository;

    @Autowired
    private NotificationEventRepository notificationEventRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void receiversGiven_insertAll_insertsRowsAndReturnsIdsByReceiver() {
        NotificationEvent event = saveEvent("bulk-insert");
        LocalDateTime createdAt = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
        LocalDateTime expiresAt = createdAt.plusDays(30);

        Map<Long, Long> idByReceiver = notificationBulkRepository.insertAll(
                event.getId(), List.of(1L, 2L, 3L), createdAt, expiresAt);
        entityManager.clear();

        assertThat(idByReceiver).containsOnlyKeys(1L, 2L, 3L);
        assertThat(idByReceiver.values()).doesNotHaveDuplicates();
        idByReceiver.forEach((receiverUserId, notificationId) ->
                assertThat(notificationRepository.findOneById(notificationId)).get().satisfies(found -> {
                    assertThat(found.getReceiverUserId()).isEqualTo(receiverUserId);
                    assertThat(found.getEvent().getId()).isEqualTo(event.getId());
                    assertThat(found.getCreatedAt()).isEqualTo(createdAt);
                    assertThat(found.getUpdatedAt()).isEqualTo(createdAt);
                    assertThat(found.getExpiresAt()).isEqualTo(expiresAt);
                    assertThat(found.getReadAt()).isNull();
                    assertThat(found.getDeletedAt()).isNull();
                }));
    }

    @Test
    void otherEventHasNotifications_insertAll_returnsOnlyThisEventIds() {
        NotificationEvent other = saveEvent("bulk-other");
        LocalDateTime now = LocalDateTime.now();
        notificationBulkRepository.insertAll(other.getId(), List.of(1L, 2L), now, now.plusDays(1));
        NotificationEvent event = saveEvent("bulk-target");

        Map<Long, Long> idByReceiver = notificationBulkRepository.insertAll(
                event.getId(), List.of(1L), now, now.plusDays(1));

        assertThat(idByReceiver).containsOnlyKeys(1L);
        assertThat(notificationRepository.findAll())
                .filteredOn(notification -> notification.getEvent().getId().equals(event.getId()))
                .extracting(Notification::getId)
                .containsExactly(idByReceiver.get(1L));
    }

    @Test
    void rowsOfSeveralEvents_insertAll_returnsIdsByEventAndReceiver() {
        NotificationEvent first = saveEvent("bulk-first");
        NotificationEvent second = saveEvent("bulk-second");
        LocalDateTime createdAt = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
        LocalDateTime firstExpiresAt = createdAt.plusDays(1);
        LocalDateTime secondExpiresAt = createdAt.plusDays(2);

        Map<Long, Map<Long, Long>> idsByEvent = notificationBulkRepository.insertAll(List.of(
                new NotificationInsertRow(first.getId(), 1L, firstExpiresAt),
                new NotificationInsertRow(first.getId(), 2L, firstExpiresAt),
                new NotificationInsertRow(second.getId(), 1L, secondExpiresAt)
        ), createdAt);
        entityManager.clear();

        assertThat(idsByEvent).containsOnlyKeys(first.getId(), second.getId());
        assertThat(idsByEvent.get(first.getId())).containsOnlyKeys(1L, 2L);
        assertThat(idsByEvent.get(second.getId())).containsOnlyKeys(1L);
        assertThat(notificationRepository.findOneById(idsByEvent.get(second.getId()).get(1L))).get()
                .satisfies(found -> {
                    assertThat(found.getEvent().getId()).isEqualTo(second.getId());
                    assertThat(found.getExpiresAt()).isEqualTo(secondExpiresAt);
                });
    }

    @Test
    void rowsMissing_insertAll_returnsEmptyMap() {
        assertThat(notificationBulkRepository.insertAll(List.of(), LocalDateTime.now())).isEmpty();
    }

    private NotificationEvent saveEvent(String dedupKey) {
        return notificationEventRepository.saveAndFlush(NotificationEvent.create(
                NotificationType.FRIEND_BIRTHDAY,
                null,
                "알림 제목",
                "알림 내용",
                NotificationResourceType.USER,
                10L,
                dedupKey
        ));
    }
}
