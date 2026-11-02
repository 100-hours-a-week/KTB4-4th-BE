package kr.ktb.zura.needu.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import kr.ktb.zura.needu.notification.entity.Notification;
import kr.ktb.zura.needu.notification.entity.NotificationEvent;
import kr.ktb.zura.needu.notification.type.NotificationResourceType;
import kr.ktb.zura.needu.notification.type.NotificationType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;

// 생성 키 반환은 MySQL 드라이버가 배치를 다중 행 INSERT로 합칠 때(rewriteBatchedStatements)의 동작을 확인해야 하므로
// integration 프로필의 MySQL을 사용한다.
@DataJpaTest(properties = "spring.datasource.hikari.data-source-properties.rewriteBatchedStatements=true")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class NotificationRepositoryCustomImplIntegrationTest {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationEventRepository notificationEventRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void rewrittenBatch_insertAll_returnsIdsMatchingReceiverOrder() {
        NotificationEvent event = createEvent("rewritten-batch");
        LocalDateTime createdAt = LocalDateTime.of(2026, 11, 2, 0, 0);
        List<Long> receiverUserIds = LongStream.rangeClosed(1, 300)
                .map(index -> 1_000 - index)
                .boxed()
                .toList();

        List<Long> ids = notificationRepository.insertAll(
                event.getId(), receiverUserIds, createdAt, createdAt.plusDays(7));
        entityManager.clear();

        assertThat(ids).hasSize(300).doesNotHaveDuplicates();
        Map<Long, Long> receiverUserIdById = notificationRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Notification::getId, Notification::getReceiverUserId));
        assertThat(ids).extracting(receiverUserIdById::get).containsExactlyElementsOf(receiverUserIds);
    }

    @Test
    void receiverAlreadyNotified_insertAll_throwsDataIntegrityViolation() {
        NotificationEvent event = createEvent("duplicate-receiver");
        LocalDateTime createdAt = LocalDateTime.of(2026, 11, 2, 0, 0);
        notificationRepository.insertAll(event.getId(), List.of(1L), createdAt, createdAt.plusDays(7));

        assertThatThrownBy(() -> notificationRepository.insertAll(
                event.getId(), List.of(2L, 1L), createdAt, createdAt.plusDays(7)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private NotificationEvent createEvent(String dedupKey) {
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
