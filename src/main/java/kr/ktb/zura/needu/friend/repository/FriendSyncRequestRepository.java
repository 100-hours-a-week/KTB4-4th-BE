package kr.ktb.zura.needu.friend.repository;

import java.time.LocalDateTime;
import java.util.List;
import kr.ktb.zura.needu.friend.entity.FriendSyncRequest;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FriendSyncRequestRepository extends JpaRepository<FriendSyncRequest, Long> {

    @Modifying
    @Query("delete from FriendSyncRequest request where request.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);

    @Modifying
    @Query(value = """
            insert ignore into friend_sync_requests (user_id, requested_at, next_attempt_at, retry_count)
            values (:userId, :requestedAt, :requestedAt, 0)
            """, nativeQuery = true)
    int createIfAbsent(@Param("userId") Long userId, @Param("requestedAt") LocalDateTime requestedAt);

    List<FriendSyncRequest> findAllByNextAttemptAtLessThanEqualOrderByNextAttemptAtAscUserIdAsc(
            LocalDateTime currentTime,
            Limit limit
    );
}
