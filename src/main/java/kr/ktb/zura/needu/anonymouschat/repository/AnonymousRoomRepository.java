package kr.ktb.zura.needu.anonymouschat.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import jakarta.persistence.LockModeType;
import kr.ktb.zura.needu.anonymouschat.entity.AnonymousRoom;
import kr.ktb.zura.needu.anonymouschat.type.AnonymousRoomStatus;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnonymousRoomRepository extends JpaRepository<AnonymousRoom, Long> {

    List<AnonymousRoom> findAllByIdInAndRecipientUserId(Collection<Long> ids, Long recipientUserId);

    Optional<AnonymousRoom> findFirstByProductIdAndQuestionerUserIdAndRecipientUserIdAndStatusAndExpiresAtIsNullOrderByIdDesc(
            Long productId, Long questionerUserId, Long recipientUserId, AnonymousRoomStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select room from AnonymousRoom room where room.id = :roomId")
    Optional<AnonymousRoom> findByIdForUpdate(@Param("roomId") Long roomId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select room
            from AnonymousRoom room
            where room.status = :status
              and (room.questionerUserId = :userId or room.recipientUserId = :userId)
            """)
    List<AnonymousRoom> findAllParticipatingByUserIdAndStatusForUpdate(
            @Param("userId") Long userId,
            @Param("status") AnonymousRoomStatus status
    );

    @Query("""
            select distinct room.questionerAlias
            from AnonymousRoom room
            where room.recipientUserId = :recipientUserId
              and room.expiresAt is null
            """)
    Set<String> findAllActiveQuestionerAliasesByRecipientUserId(
            @Param("recipientUserId") Long recipientUserId
    );

    @Query("""
            select new kr.ktb.zura.needu.anonymouschat.repository.AnonymousRoomListRow(
                room.id, room.productId, room.recipientUserId, room.questionerAlias, room.lastActivityAt)
            from AnonymousRoom room
            where room.questionerUserId = :userId
              and (:cursorActivityAt is null
                or room.lastActivityAt < :cursorActivityAt
                or (room.lastActivityAt = :cursorActivityAt and room.id < :cursorRoomId))
            order by room.lastActivityAt desc, room.id desc
            """)
    List<AnonymousRoomListRow> findAllAskedByUserId(
            @Param("userId") Long userId,
            @Param("cursorActivityAt") LocalDateTime cursorActivityAt,
            @Param("cursorRoomId") Long cursorRoomId,
            Limit limit
    );

    @Query("""
            select new kr.ktb.zura.needu.anonymouschat.repository.AnonymousRoomListRow(
                room.id, room.productId, room.recipientUserId, room.questionerAlias, room.lastActivityAt)
            from AnonymousRoom room
            where room.recipientUserId = :userId
              and (:cursorActivityAt is null
                or room.lastActivityAt < :cursorActivityAt
                or (room.lastActivityAt = :cursorActivityAt and room.id < :cursorRoomId))
            order by room.lastActivityAt desc, room.id desc
            """)
    List<AnonymousRoomListRow> findAllReceivedByUserId(
            @Param("userId") Long userId,
            @Param("cursorActivityAt") LocalDateTime cursorActivityAt,
            @Param("cursorRoomId") Long cursorRoomId,
            Limit limit
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AnonymousRoom room
            set room.lastActivityAt = :activityAt
            where room.id = :roomId
              and room.lastActivityAt < :activityAt
            """)
    int updateLastActivityAt(
            @Param("roomId") Long roomId,
            @Param("activityAt") LocalDateTime activityAt
    );

    @Modifying
    @Query("""
            update AnonymousRoom room
            set room.questionerLastReadMessageId = :messageId
            where room.id = :roomId
              and (room.questionerLastReadMessageId is null or room.questionerLastReadMessageId < :messageId)
            """)
    int updateQuestionerLastReadMessageId(@Param("roomId") Long roomId, @Param("messageId") Long messageId);

    @Modifying
    @Query("""
            update AnonymousRoom room
            set room.recipientLastReadMessageId = :messageId
            where room.id = :roomId
              and (room.recipientLastReadMessageId is null or room.recipientLastReadMessageId < :messageId)
            """)
    int updateRecipientLastReadMessageId(@Param("roomId") Long roomId, @Param("messageId") Long messageId);
}
