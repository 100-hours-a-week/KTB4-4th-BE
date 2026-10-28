package kr.ktb.zura.needu.anonymouschat.repository;

import java.util.Collection;
import java.util.List;
import kr.ktb.zura.needu.anonymouschat.entity.AnonymousMessage;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnonymousMessageRepository extends JpaRepository<AnonymousMessage, Long> {

    @Query("""
            select new kr.ktb.zura.needu.anonymouschat.repository.AnonymousRoomLastMessage(
                message.room.id, message.id, message.content, message.senderUserId, message.createdAt)
            from AnonymousMessage message
            where message.room.id in :roomIds
              and message.deletedAt is null
              and message.id = (
                select max(latest.id)
                from AnonymousMessage latest
                where latest.room.id = message.room.id
                  and latest.deletedAt is null
              )
            """)
    List<AnonymousRoomLastMessage> findAllLastMessagesByRoomIdIn(
            @Param("roomIds") Collection<Long> roomIds
    );

    @Query("""
            select new kr.ktb.zura.needu.anonymouschat.repository.AnonymousRoomUnreadCount(
                message.room.id, count(message.id))
            from AnonymousMessage message
            where message.room.id in :roomIds
              and message.senderUserId <> :userId
              and message.id > coalesce(
                case when message.room.questionerUserId = :userId
                  then message.room.questionerLastReadMessageId
                  else message.room.recipientLastReadMessageId
                end, 0)
              and message.deletedAt is null
            group by message.room.id
            """)
    List<AnonymousRoomUnreadCount> countAllUnreadByRoomIdIn(
            @Param("roomIds") Collection<Long> roomIds,
            @Param("userId") Long userId
    );

    boolean existsByIdAndRoomId(Long id, Long roomId);

    @Query("""
            select message
            from AnonymousMessage message
            where message.room.id = :roomId
              and message.deletedAt is null
              and (:cursorMessageId is null or message.id < :cursorMessageId)
            order by message.id desc
            """)
    List<AnonymousMessage> findAllLatestByRoomId(
            @Param("roomId") Long roomId,
            @Param("cursorMessageId") Long cursorMessageId,
            Limit limit
    );
}
