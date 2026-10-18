package kr.ktb.zura.needu.notification.repository;

import java.util.List;

import kr.ktb.zura.needu.notification.entity.NotificationEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationEventRepository extends JpaRepository<NotificationEvent, Long> {

    List<NotificationEvent> findAllByActorUserId(Long actorUserId);

    boolean existsByDedupKey(String dedupKey);
}
