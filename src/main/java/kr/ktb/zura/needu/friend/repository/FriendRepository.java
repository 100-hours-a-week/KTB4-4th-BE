package kr.ktb.zura.needu.friend.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import kr.ktb.zura.needu.friend.dto.response.FriendBirthdayNotificationTarget;
import kr.ktb.zura.needu.friend.dto.response.FriendSummaryResponse;
import kr.ktb.zura.needu.friend.entity.Friend;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FriendRepository extends JpaRepository<Friend, Long> {

    boolean existsByOwnerUserIdAndFriendUserId(Long ownerUserId, Long friendUserId);

    Optional<Friend> findByOwnerUserIdAndFriendUserId(Long ownerUserId, Long friendUserId);

    List<Friend> findAllByOwnerUserIdAndFriendUserIdIn(Long ownerUserId, Collection<Long> friendUserIds);

    @Query("""
            select new kr.ktb.zura.needu.friend.dto.response.FriendBirthdayNotificationTarget(
                f.ownerUserId, friendUser.id, friendUser.nickname)
            from Friend f
            join User friendUser on friendUser.id = f.friendUserId
            join User ownerUser on ownerUser.id = f.ownerUserId
            where friendUser.status = kr.ktb.zura.needu.user.type.UserStatus.ACTIVE
              and ownerUser.status = kr.ktb.zura.needu.user.type.UserStatus.ACTIVE
              and (
                  (month(friendUser.birthDate) = :month and day(friendUser.birthDate) = :day)
                  or (:includeLeapDay = true
                      and month(friendUser.birthDate) = 2
                      and day(friendUser.birthDate) = 29)
              )
            """)
    List<FriendBirthdayNotificationTarget> findAllBirthdayNotificationTargets(
            @Param("month") int month,
            @Param("day") int day,
            @Param("includeLeapDay") boolean includeLeapDay
    );

    // namePrefixPattern의 이스케이프 문자 '!'는 FriendService에서 만드는 패턴과 동일해야 한다
    @Query("""
            select new kr.ktb.zura.needu.friend.dto.response.FriendSummaryResponse(
                u.id, u.nickname, u.profileImageUrl, u.birthDate, f.favorite)
            from Friend f
            join User u on u.id = f.friendUserId
            where f.ownerUserId = :ownerUserId
              and u.status = kr.ktb.zura.needu.user.type.UserStatus.ACTIVE
              and (:isFavoriteOnly = false or f.favorite = true)
              and u.nickname like :namePrefixPattern escape '!'
            order by u.nickname asc, u.id asc
            """)
    List<FriendSummaryResponse> findAllByOwnerUserIdOrderByName(
            @Param("ownerUserId") Long ownerUserId,
            @Param("isFavoriteOnly") boolean isFavoriteOnly,
            @Param("namePrefixPattern") String namePrefixPattern,
            Limit limit
    );

    @Query("""
            select new kr.ktb.zura.needu.friend.dto.response.FriendSummaryResponse(
                u.id, u.nickname, u.profileImageUrl, u.birthDate, f.favorite)
            from Friend f
            join User u on u.id = f.friendUserId
            where f.ownerUserId = :ownerUserId
              and u.status = kr.ktb.zura.needu.user.type.UserStatus.ACTIVE
              and (:isFavoriteOnly = false or f.favorite = true)
              and u.nickname like :namePrefixPattern escape '!'
              and (u.nickname > :cursorName or (u.nickname = :cursorName and u.id > :cursorUserId))
            order by u.nickname asc, u.id asc
            """)
    List<FriendSummaryResponse> findAllByOwnerUserIdAfterNameCursor(
            @Param("ownerUserId") Long ownerUserId,
            @Param("isFavoriteOnly") boolean isFavoriteOnly,
            @Param("namePrefixPattern") String namePrefixPattern,
            @Param("cursorName") String cursorName,
            @Param("cursorUserId") Long cursorUserId,
            Limit limit
    );
}
