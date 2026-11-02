package kr.ktb.zura.needu.notification.repository;

import java.util.Collection;
import java.util.List;

import kr.ktb.zura.needu.notification.entity.NotificationEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationEventRepository extends JpaRepository<NotificationEvent, Long> {

    List<NotificationEvent> findAllByActorUserId(Long actorUserId);

    @Query("SELECT event.dedupKey FROM NotificationEvent event WHERE event.dedupKey IN :dedupKeys")
    List<String> findAllDedupKeysByDedupKeyIn(@Param("dedupKeys") Collection<String> dedupKeys);
}
