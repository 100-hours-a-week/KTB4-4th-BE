package kr.ktb.zura.needu.notification.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import kr.ktb.zura.needu.notification.entity.PushSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

    Optional<PushSubscription> findByEndpointHash(String endpointHash);

    List<PushSubscription> findAllByUserId(Long userId);

    List<PushSubscription> findAllByUserIdIn(Collection<Long> userIds);

    boolean existsByEndpointHashAndUserId(String endpointHash, Long userId);

    void deleteByEndpointHashAndUserId(String endpointHash, Long userId);

    void deleteAllByUserId(Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            INSERT INTO push_subscriptions (
                user_id, endpoint, endpoint_hash, p256dh, auth, created_at, updated_at
            ) VALUES (
                :userId, :endpoint, :endpointHash, :p256dh, :auth, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
            )
            ON DUPLICATE KEY UPDATE
                user_id = VALUES(user_id),
                endpoint = VALUES(endpoint),
                p256dh = VALUES(p256dh),
                auth = VALUES(auth),
                updated_at = UTC_TIMESTAMP(6)
            """, nativeQuery = true)
    void upsert(
            @Param("userId") Long userId,
            @Param("endpoint") String endpoint,
            @Param("endpointHash") String endpointHash,
            @Param("p256dh") String p256dh,
            @Param("auth") String auth
    );
}
