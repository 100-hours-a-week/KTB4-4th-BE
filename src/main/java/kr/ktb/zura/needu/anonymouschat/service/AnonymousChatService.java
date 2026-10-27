package kr.ktb.zura.needu.anonymouschat.service;

import kr.ktb.zura.needu.anonymouschat.dto.response.AnonymousRoomDetailResponse;
import kr.ktb.zura.needu.anonymouschat.entity.AnonymousRoom;
import kr.ktb.zura.needu.anonymouschat.exception.AnonymousChatErrorCode;
import kr.ktb.zura.needu.anonymouschat.repository.AnonymousRoomRepository;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.product.service.ProductService;
import kr.ktb.zura.needu.user.dto.response.UserDetailResponse;
import kr.ktb.zura.needu.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AnonymousChatService {

    private final AnonymousRoomRepository anonymousRoomRepository;
    private final UserService userService;
    private final ProductService productService;

    public void findAllRooms() {
    }

    public void createRoom() {
    }

    @Transactional(readOnly = true)
    public AnonymousRoomDetailResponse findRoom(Long userId, Long roomId) {
        userService.validateActiveUser(userId);
        AnonymousRoom room = findParticipatingRoom(userId, roomId);
        boolean askedByMe = room.isQuestioner(userId);

        return AnonymousRoomDetailResponse.of(
                room,
                askedByMe,
                findTargetName(room, askedByMe),
                productService.findProductSummary(room.getProductId())
        );
    }

    public void reportChat() {
    }

    public void leaveRoom() {
    }

    public AnonymousRoom findParticipatingRoom(Long userId, Long roomId) {
        AnonymousRoom room = anonymousRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(AnonymousChatErrorCode.ANONYMOUS_CHAT_ROOM_NOT_FOUND));
        if (!room.isParticipant(userId)) {
            throw new BusinessException(AnonymousChatErrorCode.ANONYMOUS_CHAT_ROOM_FORBIDDEN);
        }
        return room;
    }

    private String findTargetName(AnonymousRoom room, boolean askedByMe) {
        if (!askedByMe) {
            return room.getQuestionerAlias();
        }
        return userService.findUserById(room.getRecipientUserId())
                .map(UserDetailResponse::nickname)
                .orElse(null);
    }
}
