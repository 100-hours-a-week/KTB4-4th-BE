package kr.ktb.zura.needu.friend.repository;

import kr.ktb.zura.needu.friend.entity.FriendSyncRequest;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FriendSyncRequestRepository extends JpaRepository<FriendSyncRequest, Long> {
}
