package kr.ktb.zura.needu.friend.repository;

import java.util.Optional;

import kr.ktb.zura.needu.friend.entity.Friend;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FriendRepository extends JpaRepository<Friend, Long> {

    @Query("""
            select f
            from Friend f
            join fetch f.friendUser fu
            where f.ownerUser.id = :ownerUserId
              and fu.id = :friendUserId
              and fu.status = kr.ktb.zura.needu.user.type.UserStatus.ACTIVE
            """)
    Optional<Friend> findActiveFriend(@Param("ownerUserId") Long ownerUserId,
                                      @Param("friendUserId") Long friendUserId);
}
