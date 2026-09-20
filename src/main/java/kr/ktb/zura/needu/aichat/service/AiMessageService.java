package kr.ktb.zura.needu.aichat.service;

import java.util.Optional;
import java.util.UUID;

import kr.ktb.zura.needu.aichat.entity.AiChatRoom;
import kr.ktb.zura.needu.aichat.entity.AiMessage;
import kr.ktb.zura.needu.aichat.repository.AiChatRoomRepository;
import kr.ktb.zura.needu.aichat.repository.AiMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// AI 호출 동안 트랜잭션을 유지하지 않도록 대화 메시지 저장만 짧은 트랜잭션으로 나눠 담당
@Service
@RequiredArgsConstructor
public class AiMessageService {

    private final AiMessageRepository aiMessageRepository;
    private final AiChatRoomRepository aiChatRoomRepository;

    @Transactional(readOnly = true)
    public Optional<AiMessage> findUserMessage(Long conversationId, UUID clientMessageId) {
        return aiMessageRepository.findByAiChatRoomIdAndClientMessageId(conversationId, clientMessageId);
    }

    @Transactional(readOnly = true)
    public Optional<AiMessage> findReply(Long userMessageId) {
        return aiMessageRepository.findByReplyToMessageId(userMessageId);
    }

    // AI 호출 전에 ID가 필요하고 clientMessageId 유일 제약 위반을 즉시 확인해야 하므로 바로 flush 한다
    @Transactional
    public AiMessage createUserMessage(Long conversationId, UUID clientMessageId, String content) {
        AiChatRoom room = aiChatRoomRepository.getReferenceById(conversationId);
        return aiMessageRepository.saveAndFlush(AiMessage.createUserMessage(room, clientMessageId, content));
    }

    @Transactional
    public AiMessage createReply(Long conversationId, Long userMessageId, String content) {
        AiChatRoom room = aiChatRoomRepository.getReferenceById(conversationId);
        return aiMessageRepository.save(AiMessage.createReply(room, userMessageId, content));
    }
}
