package kr.ktb.zura.needu.aichat.repository;

import java.util.Optional;
import java.util.UUID;

import kr.ktb.zura.needu.aichat.entity.AiMessage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiMessageRepository extends JpaRepository<AiMessage, Long> {

    Optional<AiMessage> findByAiChatRoomIdAndClientMessageId(Long aiChatRoomId, UUID clientMessageId);

    Optional<AiMessage> findByReplyToMessageId(Long replyToMessageId);
}
