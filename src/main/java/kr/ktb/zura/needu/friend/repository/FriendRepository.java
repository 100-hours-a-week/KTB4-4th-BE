package kr.ktb.zura.needu.friend.repository;

import java.util.Collection;
import java.util.List;
import kr.ktb.zura.needu.friend.entity.Friend;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FriendRepository extends JpaRepository<Friend, Long> {

    boolean existsByOwnerUserIdAndFriendUserId(Long ownerUserId, Long friendUserId);

    List<Friend> findAllByOwnerUserIdOrderByIdAsc(Long ownerUserId);

    List<Friend> findAllByOwnerUserIdAndFriendUserIdIn(Long ownerUserId, Collection<Long> friendUserIds);
}
