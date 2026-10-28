package kr.ktb.zura.needu.anonymouschat.service;

import java.util.List;
import kr.ktb.zura.needu.anonymouschat.dto.request.AnonymousMessageSearchCondition;
import kr.ktb.zura.needu.anonymouschat.dto.response.AnonymousMessageListResponse;
import kr.ktb.zura.needu.anonymouschat.dto.response.AnonymousMessageSummaryResponse;
import kr.ktb.zura.needu.anonymouschat.entity.AnonymousMessage;
import kr.ktb.zura.needu.anonymouschat.entity.AnonymousRoom;
import kr.ktb.zura.needu.anonymouschat.repository.AnonymousMessageRepository;
import kr.ktb.zura.needu.anonymouschat.repository.AnonymousRoomRepository;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.CommonErrorCode;
import kr.ktb.zura.needu.common.response.CursorPageResponse;
import kr.ktb.zura.needu.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AnonymousMessageService {

    private final AnonymousChatService anonymousChatService;
    private final AnonymousRoomRepository anonymousRoomRepository;
    private final AnonymousMessageRepository anonymousMessageRepository;
    private final UserService userService;

    @Transactional(readOnly = true)
    public AnonymousMessageListResponse findAllMessages(
            Long userId, Long roomId, AnonymousMessageSearchCondition condition) {
        userService.validateActiveUser(userId);
        AnonymousRoom room = anonymousChatService.findParticipatingRoom(userId, roomId);

        return new AnonymousMessageListResponse(
                anonymousChatService.findTargetName(room, room.isQuestioner(userId)),
                findMessagePage(userId, room, condition)
        );
    }

    @Transactional
    public void readMessages(Long userId, Long roomId, Long lastReadMessageId) {
        userService.validateActiveUser(userId);
        AnonymousRoom room = anonymousChatService.findParticipatingRoom(userId, roomId);
        validateMessageInRoom(roomId, lastReadMessageId);

        if (room.isQuestioner(userId)) {
            anonymousRoomRepository.updateQuestionerLastReadMessageId(roomId, lastReadMessageId);
            return;
        }

        anonymousRoomRepository.updateRecipientLastReadMessageId(roomId, lastReadMessageId);
    }

    private CursorPageResponse<AnonymousMessageSummaryResponse> findMessagePage(
            Long userId, AnonymousRoom room, AnonymousMessageSearchCondition condition) {
        List<AnonymousMessage> messages = findLatestMessages(room.getId(), condition.cursor(), condition.size() + 1);
        boolean hasNext = messages.size() > condition.size();
        List<AnonymousMessage> pageMessages = hasNext ? messages.subList(0, condition.size()) : messages;

        String nextCursor = hasNext ? AnonymousMessageCursor.from(pageMessages.getLast()).encode() : null;
        return new CursorPageResponse<>(toMessageResponses(userId, room, pageMessages), nextCursor, hasNext);
    }

    private List<AnonymousMessage> findLatestMessages(Long roomId, String cursor, int limit) {
        Long cursorMessageId = cursor == null ? null : AnonymousMessageCursor.decode(cursor).messageId();
        return anonymousMessageRepository.findAllLatestByRoomId(roomId, cursorMessageId, Limit.of(limit));
    }

    private List<AnonymousMessageSummaryResponse> toMessageResponses(
            Long userId, AnonymousRoom room, List<AnonymousMessage> messages) {
        Long partnerLastReadMessageId = room.isQuestioner(userId)
                ? room.getRecipientLastReadMessageId()
                : room.getQuestionerLastReadMessageId();
        return messages.stream()
                .map(message -> AnonymousMessageSummaryResponse.from(message, userId, partnerLastReadMessageId))
                .toList();
    }

    private void validateMessageInRoom(Long roomId, Long messageId) {
        if (!anonymousMessageRepository.existsByIdAndRoomId(messageId, roomId)) {
            throw new BusinessException(CommonErrorCode.COMMON_INVALID_INPUT);
        }
    }
}
