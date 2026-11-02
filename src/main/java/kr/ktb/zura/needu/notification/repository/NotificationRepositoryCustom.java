package kr.ktb.zura.needu.notification.repository;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificationRepositoryCustom {

    /**
     * 한 알림 이벤트의 수신자 알림을 JDBC 배치로 저장한다.
     * IDENTITY 키 엔티티는 Hibernate가 JDBC 배치를 끄므로 행마다 INSERT하지 않도록 JPA를 거치지 않는다.
     *
     * @return 생성된 알림 ID, {@code receiverUserIds}와 같은 순서
     */
    List<Long> insertAll(
            Long eventId,
            List<Long> receiverUserIds,
            LocalDateTime createdAt,
            LocalDateTime expiresAt
    );
}
