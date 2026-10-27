package kr.ktb.zura.needu.anonymouschat.repository;

import kr.ktb.zura.needu.anonymouschat.entity.AnonymousRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnonymousRoomRepository extends JpaRepository<AnonymousRoom, Long> {

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
