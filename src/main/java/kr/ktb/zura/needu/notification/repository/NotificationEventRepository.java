package kr.ktb.zura.needu.notification.repository;

import java.util.Collection;
import java.util.List;

import kr.ktb.zura.needu.notification.entity.NotificationEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationEventRepository extends JpaRepository<NotificationEvent, Long> {

    List<NotificationEvent> findAllByActorUserId(Long actorUserId);

    boolean existsByDedupKey(String dedupKey);

    @Query("select event.dedupKey from NotificationEvent event where event.dedupKey in :dedupKeys")
    List<String> findAllDedupKeysIn(@Param("dedupKeys") Collection<String> dedupKeys);
}
