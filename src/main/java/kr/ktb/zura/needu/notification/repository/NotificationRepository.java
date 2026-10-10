package kr.ktb.zura.needu.notification.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import kr.ktb.zura.needu.notification.entity.Notification;
import kr.ktb.zura.needu.notification.type.NotificationType;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @EntityGraph(attributePaths = "event")
    Optional<Notification> findOneById(Long id);

    @Query("""
            SELECT COUNT(notification)
            FROM Notification notification
            WHERE notification.receiverUserId = :receiverUserId
              AND notification.readAt IS NULL
              AND notification.deletedAt IS NULL
              AND notification.expiresAt > CURRENT_TIMESTAMP
            """)
    long countUnreadByReceiverUserId(@Param("receiverUserId") Long receiverUserId);

    @Query("""
            SELECT notification
            FROM Notification notification
            JOIN FETCH notification.event event
            WHERE notification.receiverUserId = :receiverUserId
              AND notification.deletedAt IS NULL
              AND notification.expiresAt > CURRENT_TIMESTAMP
              AND event.type IN :types
              AND (:since IS NULL OR notification.createdAt > :since)
              AND (:cursorId IS NULL OR notification.id < :cursorId)
            ORDER BY notification.id DESC
            """)
    List<Notification> findAllVisibleByReceiverUserId(
            @Param("receiverUserId") Long receiverUserId,
            @Param("types") Collection<NotificationType> types,
            @Param("since") LocalDateTime since,
            @Param("cursorId") Long cursorId,
            Limit limit
    );

    @Query("""
            SELECT COUNT(notification)
            FROM Notification notification
            JOIN notification.event event
            WHERE notification.receiverUserId = :receiverUserId
              AND notification.deletedAt IS NULL
              AND notification.expiresAt > CURRENT_TIMESTAMP
              AND event.type IN :types
              AND notification.createdAt > :since
            """)
    long countNewByReceiverUserId(
            @Param("receiverUserId") Long receiverUserId,
            @Param("types") Collection<NotificationType> types,
            @Param("since") LocalDateTime since
    );
}
