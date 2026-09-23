package kr.ktb.zura.needu.aichat.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import kr.ktb.zura.needu.aichat.dto.response.AiMessageSummaryResponse;
import kr.ktb.zura.needu.aichat.entity.AiChatRoom;
import kr.ktb.zura.needu.aichat.entity.AiMessage;
import kr.ktb.zura.needu.aichat.repository.AiChatRoomRepository;
import kr.ktb.zura.needu.aichat.repository.AiMessageRepository;
import kr.ktb.zura.needu.common.response.CursorPageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
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

    @Transactional(readOnly = true)
    public CursorPageResponse<AiMessageSummaryResponse> findAllMessages(Long conversationId, String cursor, int size) {
        // 다음 페이지 존재 여부를 추가 count 쿼리 없이 판단하기 위해 한 건을 더 조회
        List<AiMessage> messages = findLatestMessages(conversationId, cursor, Limit.of(size + 1));
        boolean hasNext = messages.size() > size;
        List<AiMessage> pageItems = hasNext ? messages.subList(0, size) : messages;

        String nextCursor = hasNext ? AiMessageCursor.from(pageItems.getLast()).encode() : null;
        return new CursorPageResponse<>(toOldestFirst(pageItems), nextCursor, hasNext);
    }

    private List<AiMessage> findLatestMessages(Long conversationId, String cursor, Limit limit) {
        if (cursor == null) {
            return aiMessageRepository.findAllByAiChatRoomId(conversationId, limit);
        }
        AiMessageCursor decodedCursor = AiMessageCursor.decode(cursor);
        return aiMessageRepository.findAllByAiChatRoomIdBeforeCursor(
                conversationId, decodedCursor.messageId(), limit);
    }

    // 커서 페이지네이션은 최신순으로 조회하지만, 화면은 시간 오름차순으로 그리므로 뒤집어 반환
    private List<AiMessageSummaryResponse> toOldestFirst(List<AiMessage> messages) {
        return messages.reversed().stream()
                .map(AiMessageSummaryResponse::from)
                .toList();
    }

    // AI 호출 전에 ID가 필요하고 clientMessageId 유일 제약 위반을 즉시 확인해야 하므로 바로 flush
    @Transactional
    public AiMessage createUserMessage(Long conversationId, UUID clientMessageId, String content) {
        AiChatRoom room = aiChatRoomRepository.getReferenceById(conversationId);
        return aiMessageRepository.saveAndFlush(AiMessage.createUserMessage(room, clientMessageId, content));
    }

    @Transactional
    public AiMessage createReply(Long conversationId, Long userMessageId, String content,
                                 int progress, boolean inputLocked) {
        AiChatRoom room = aiChatRoomRepository.getReferenceById(conversationId);
        return aiMessageRepository.save(
                AiMessage.createReply(room, userMessageId, content, progress, inputLocked));
    }
}
