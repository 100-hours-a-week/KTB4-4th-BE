package kr.ktb.zura.needu.friend.repository;

import java.util.Collection;
import java.util.List;
import kr.ktb.zura.needu.friend.dto.response.FriendSummaryResponse;
import kr.ktb.zura.needu.friend.entity.Friend;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FriendRepository extends JpaRepository<Friend, Long> {

    boolean existsByOwnerUserIdAndFriendUserId(Long ownerUserId, Long friendUserId);

    List<Friend> findAllByOwnerUserIdAndFriendUserIdIn(Long ownerUserId, Collection<Long> friendUserIds);

    @Query("""
            select new kr.ktb.zura.needu.friend.dto.response.FriendSummaryResponse(
                u.id, u.nickname, u.profileImageUrl, u.birthDate, f.favorite)
            from Friend f
            join User u on u.id = f.friendUserId
            where f.ownerUserId = :ownerUserId
            order by u.nickname asc, u.id asc
            """)
    List<FriendSummaryResponse> findAllByOwnerUserIdOrderByName(
            @Param("ownerUserId") Long ownerUserId,
            Limit limit
    );

    @Query("""
            select new kr.ktb.zura.needu.friend.dto.response.FriendSummaryResponse(
                u.id, u.nickname, u.profileImageUrl, u.birthDate, f.favorite)
            from Friend f
            join User u on u.id = f.friendUserId
            where f.ownerUserId = :ownerUserId
              and (u.nickname > :cursorName or (u.nickname = :cursorName and u.id > :cursorUserId))
            order by u.nickname asc, u.id asc
            """)
    List<FriendSummaryResponse> findAllByOwnerUserIdAfterNameCursor(
            @Param("ownerUserId") Long ownerUserId,
            @Param("cursorName") String cursorName,
            @Param("cursorUserId") Long cursorUserId,
            Limit limit
    );

    @Query("""
            select new kr.ktb.zura.needu.friend.dto.response.FriendSummaryResponse(
                u.id, u.nickname, u.profileImageUrl, u.birthDate, f.favorite)
            from Friend f
            join User u on u.id = f.friendUserId
            where f.ownerUserId = :ownerUserId
              and u.birthDate is not null
            order by
              case
                when (case when month(u.birthDate) = 2 and day(u.birthDate) = 29
                           then 301 else month(u.birthDate) * 100 + day(u.birthDate) end) >= :currentBirthdayKey
                then (case when month(u.birthDate) = 2 and day(u.birthDate) = 29
                           then 301 else month(u.birthDate) * 100 + day(u.birthDate) end)
                else (case when month(u.birthDate) = 2 and day(u.birthDate) = 29
                           then 301 else month(u.birthDate) * 100 + day(u.birthDate) end) + 1200
              end asc,
              u.id asc
            """)
    List<FriendSummaryResponse> findAllByOwnerUserIdOrderByUpcomingBirthday(
            @Param("ownerUserId") Long ownerUserId,
            @Param("currentBirthdayKey") int currentBirthdayKey,
            Limit limit
    );

    @Query("""
            select new kr.ktb.zura.needu.friend.dto.response.FriendSummaryResponse(
                u.id, u.nickname, u.profileImageUrl, u.birthDate, f.favorite)
            from Friend f
            join User u on u.id = f.friendUserId
            where f.ownerUserId = :ownerUserId
              and u.birthDate is not null
              and (
                (case
                   when (case when month(u.birthDate) = 2 and day(u.birthDate) = 29
                              then 301 else month(u.birthDate) * 100 + day(u.birthDate) end) >= :currentBirthdayKey
                   then (case when month(u.birthDate) = 2 and day(u.birthDate) = 29
                              then 301 else month(u.birthDate) * 100 + day(u.birthDate) end)
                   else (case when month(u.birthDate) = 2 and day(u.birthDate) = 29
                              then 301 else month(u.birthDate) * 100 + day(u.birthDate) end) + 1200
                 end) > :cursorSortKey
                or (
                  (case
                     when (case when month(u.birthDate) = 2 and day(u.birthDate) = 29
                                then 301 else month(u.birthDate) * 100 + day(u.birthDate) end) >= :currentBirthdayKey
                     then (case when month(u.birthDate) = 2 and day(u.birthDate) = 29
                                then 301 else month(u.birthDate) * 100 + day(u.birthDate) end)
                     else (case when month(u.birthDate) = 2 and day(u.birthDate) = 29
                                then 301 else month(u.birthDate) * 100 + day(u.birthDate) end) + 1200
                   end) = :cursorSortKey
                  and u.id > :cursorUserId
                )
              )
            order by
              case
                when (case when month(u.birthDate) = 2 and day(u.birthDate) = 29
                           then 301 else month(u.birthDate) * 100 + day(u.birthDate) end) >= :currentBirthdayKey
                then (case when month(u.birthDate) = 2 and day(u.birthDate) = 29
                           then 301 else month(u.birthDate) * 100 + day(u.birthDate) end)
                else (case when month(u.birthDate) = 2 and day(u.birthDate) = 29
                           then 301 else month(u.birthDate) * 100 + day(u.birthDate) end) + 1200
              end asc,
              u.id asc
            """)
    List<FriendSummaryResponse> findAllByOwnerUserIdAfterBirthdayCursor(
            @Param("ownerUserId") Long ownerUserId,
            @Param("currentBirthdayKey") int currentBirthdayKey,
            @Param("cursorSortKey") int cursorSortKey,
            @Param("cursorUserId") Long cursorUserId,
            Limit limit
    );
}
