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
// integration 프로필의 MySQL을 사용한다. rewrite 설정은 application-integration.properties에서 운영과 같게 켠다.
@DataJpaTest
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

        List<Long> ids = notificationRepository.insertAll(receiverUserIds.stream()
                .map(receiverUserId -> new NotificationInsertRow(event.getId(), receiverUserId, createdAt.plusDays(7)))
                .toList(), createdAt);
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
        LocalDateTime expiresAt = createdAt.plusDays(7);
        notificationRepository.insertAll(
                List.of(new NotificationInsertRow(event.getId(), 1L, expiresAt)), createdAt);

        assertThatThrownBy(() -> notificationRepository.insertAll(List.of(
                new NotificationInsertRow(event.getId(), 2L, expiresAt),
                new NotificationInsertRow(event.getId(), 1L, expiresAt)
        ), createdAt))
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
