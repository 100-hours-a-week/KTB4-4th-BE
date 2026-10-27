package kr.ktb.zura.needu.anonymouschat.service;

import kr.ktb.zura.needu.anonymouschat.entity.AnonymousRoom;
import kr.ktb.zura.needu.anonymouschat.repository.AnonymousMessageRepository;
import kr.ktb.zura.needu.anonymouschat.repository.AnonymousRoomRepository;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.CommonErrorCode;
import kr.ktb.zura.needu.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AnonymousMessageService {

    private final AnonymousChatService anonymousChatService;
    private final AnonymousRoomRepository anonymousRoomRepository;
    private final AnonymousMessageRepository anonymousMessageRepository;
    private final UserService userService;

    public void findAllMessages() {
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

    private void validateMessageInRoom(Long roomId, Long messageId) {
        if (!anonymousMessageRepository.existsByIdAndRoomId(messageId, roomId)) {
            throw new BusinessException(CommonErrorCode.COMMON_INVALID_INPUT);
        }
    }
}
