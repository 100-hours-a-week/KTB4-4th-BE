package kr.ktb.zura.needu.aichat.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import kr.ktb.zura.needu.aichat.entity.AiMessage;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiMessageRepository extends JpaRepository<AiMessage, Long> {

    Optional<AiMessage> findByAiChatRoomIdAndClientMessageId(Long aiChatRoomId, UUID clientMessageId);

    Optional<AiMessage> findByReplyToMessageId(Long replyToMessageId);

    @Query("""
            select m
            from AiMessage m
            where m.aiChatRoom.id = :aiChatRoomId
              and m.deletedAt is null
            order by m.id desc
            """)
    List<AiMessage> findAllByAiChatRoomId(@Param("aiChatRoomId") Long aiChatRoomId, Limit limit);

    // 커서보다 과거(=id가 작은) 메시지만 조회한다.
    @Query("""
            select m
            from AiMessage m
            where m.aiChatRoom.id = :aiChatRoomId
              and m.deletedAt is null
              and m.id < :messageId
            order by m.id desc
            """)
    List<AiMessage> findAllByAiChatRoomIdBeforeCursor(
            @Param("aiChatRoomId") Long aiChatRoomId,
            @Param("messageId") Long messageId,
            Limit limit
    );
}
