package kr.ktb.zura.needu.anonymouschat.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import kr.ktb.zura.needu.anonymouschat.dto.request.AnonymousMessageSearchCondition;
import kr.ktb.zura.needu.anonymouschat.dto.request.SendAnonymousMessageRequest;
import kr.ktb.zura.needu.anonymouschat.dto.response.AnonymousMessageListResponse;
import kr.ktb.zura.needu.anonymouschat.dto.response.AnonymousMessageSummaryResponse;
import kr.ktb.zura.needu.anonymouschat.entity.AnonymousMessage;
import kr.ktb.zura.needu.anonymouschat.entity.AnonymousRoom;
import kr.ktb.zura.needu.anonymouschat.exception.AnonymousChatErrorCode;
import kr.ktb.zura.needu.anonymouschat.repository.AnonymousMessageRepository;
import kr.ktb.zura.needu.anonymouschat.repository.AnonymousRoomRepository;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.CommonErrorCode;
import kr.ktb.zura.needu.common.exception.TooManyRequestsException;
import kr.ktb.zura.needu.common.response.CursorPageResponse;
import kr.ktb.zura.needu.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnonymousMessageService {

    private static final long DUPLICATE_RETRY_AFTER_SECONDS = 1;

    private final AnonymousChatService anonymousChatService;
    private final AnonymousRoomRepository anonymousRoomRepository;
    private final AnonymousMessageRepository anonymousMessageRepository;
    private final UserService userService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Transactional(readOnly = true)
    public AnonymousMessageListResponse findAllMessages(
            Long userId, Long roomId, AnonymousMessageSearchCondition condition) {
        userService.validateActiveUser(userId);
        AnonymousRoom room = anonymousChatService.findParticipatingRoom(userId, roomId);

        return AnonymousMessageListResponse.of(
                room,
                userId,
                anonymousChatService.findTargetName(room, room.isQuestioner(userId)),
                findMessagePage(userId, room, condition)
        );
    }

    @Transactional
    public void sendMessage(Long userId, Long roomId, SendAnonymousMessageRequest request) {
        userService.validateActiveUser(userId);
        AnonymousRoom room = anonymousChatService.findParticipatingRoom(userId, roomId);

        Optional<AnonymousMessage> sentMessage = anonymousMessageRepository
                .findByRoomIdAndSenderUserIdAndClientMessageId(roomId, userId, request.clientMessageId());
        if (sentMessage.isPresent()) {
            log.info("Anonymous message resent. userId={}, roomId={}, messageId={}",
                    userId, roomId, sentMessage.get().getId());
            eventPublisher.publishEvent(AnonymousMessageSavedEvent.of(room, sentMessage.get(), true));
            return;
        }

        validateRoomActive(room);
        AnonymousMessage message = saveMessage(
                new AnonymousMessage(room, userId, request.clientMessageId(), request.content()));
        AnonymousMessageSavedEvent savedEvent = AnonymousMessageSavedEvent.of(room, message, false);
        anonymousRoomRepository.updateLastActivityAt(roomId, message.getCreatedAt());

        log.info("Anonymous message sent. userId={}, roomId={}, messageId={}", userId, roomId, message.getId());
        eventPublisher.publishEvent(savedEvent);
    }

    @Transactional
    public void readMessages(Long userId, Long roomId, Long lastReadMessageId) {
        userService.validateActiveUser(userId);
        AnonymousRoom room = anonymousChatService.findParticipatingRoom(userId, roomId);
        validateMessageInRoom(roomId, lastReadMessageId);

        int updatedCount = room.isQuestioner(userId)
                ? anonymousRoomRepository.updateQuestionerLastReadMessageId(roomId, lastReadMessageId)
                : anonymousRoomRepository.updateRecipientLastReadMessageId(roomId, lastReadMessageId);

        if (updatedCount > 0) {
            eventPublisher.publishEvent(new AnonymousReadPositionUpdatedEvent(
                    roomId, room.getPartnerUserId(userId), lastReadMessageId, LocalDateTime.now(clock)));
        }
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

    private void validateRoomActive(AnonymousRoom room) {
        if (room.isEnded()) {
            throw new BusinessException(AnonymousChatErrorCode.ANONYMOUS_CHAT_ROOM_ENDED);
        }
    }

    private AnonymousMessage saveMessage(AnonymousMessage message) {
        try {
            return anonymousMessageRepository.saveAndFlush(message);
        } catch (DataIntegrityViolationException e) {
            throw new TooManyRequestsException(DUPLICATE_RETRY_AFTER_SECONDS);
        }
    }

    private void validateMessageInRoom(Long roomId, Long messageId) {
        if (!anonymousMessageRepository.existsByIdAndRoomId(messageId, roomId)) {
            throw new BusinessException(CommonErrorCode.COMMON_INVALID_INPUT);
        }
    }
}
