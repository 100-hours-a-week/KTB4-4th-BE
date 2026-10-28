package kr.ktb.zura.needu.anonymouschat.service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import kr.ktb.zura.needu.anonymouschat.dto.request.AnonymousRoomSearchCondition;
import kr.ktb.zura.needu.anonymouschat.dto.response.AnonymousChatLastMessageResponse;
import kr.ktb.zura.needu.anonymouschat.dto.response.AnonymousChatProductResponse;
import kr.ktb.zura.needu.anonymouschat.dto.response.AnonymousChatRoomSummaryResponse;
import kr.ktb.zura.needu.anonymouschat.dto.response.AnonymousRoomDetailResponse;
import kr.ktb.zura.needu.anonymouschat.entity.AnonymousRoom;
import kr.ktb.zura.needu.anonymouschat.exception.AnonymousChatErrorCode;
import kr.ktb.zura.needu.anonymouschat.repository.AnonymousMessageRepository;
import kr.ktb.zura.needu.anonymouschat.repository.AnonymousRoomLastMessage;
import kr.ktb.zura.needu.anonymouschat.repository.AnonymousRoomListRow;
import kr.ktb.zura.needu.anonymouschat.repository.AnonymousRoomRepository;
import kr.ktb.zura.needu.anonymouschat.repository.AnonymousRoomUnreadCount;
import kr.ktb.zura.needu.anonymouschat.type.AnonymousChatRole;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.response.CursorPageResponse;
import kr.ktb.zura.needu.product.dto.response.ProductSummaryResponse;
import kr.ktb.zura.needu.product.service.ProductService;
import kr.ktb.zura.needu.user.dto.response.UserDetailResponse;
import kr.ktb.zura.needu.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AnonymousChatService {

    private static final String SENDER_ME = "ME";
    private static final String SENDER_OTHER = "OTHER";

    private final AnonymousRoomRepository anonymousRoomRepository;
    private final AnonymousMessageRepository anonymousMessageRepository;
    private final UserService userService;
    private final ProductService productService;

    @Transactional(readOnly = true)
    public CursorPageResponse<AnonymousChatRoomSummaryResponse> findAllRooms(
            Long userId, AnonymousRoomSearchCondition condition) {
        userService.validateActiveUser(userId);

        AnonymousRoomCursor cursor = condition.cursor() == null
                ? null
                : AnonymousRoomCursor.decode(condition.cursor());
        List<AnonymousRoomListRow> rooms = findRooms(
                userId, condition.role(), cursor, condition.size() + 1);
        boolean hasNext = rooms.size() > condition.size();
        List<AnonymousRoomListRow> pageRooms = hasNext ? rooms.subList(0, condition.size()) : rooms;
        if (pageRooms.isEmpty()) {
            return new CursorPageResponse<>(List.of(), null, false);
        }

        List<Long> roomIds = pageRooms.stream().map(AnonymousRoomListRow::roomId).toList();
        Map<Long, AnonymousRoomLastMessage> lastMessages = anonymousMessageRepository
                .findAllLastMessagesByRoomIdIn(roomIds).stream()
                .collect(Collectors.toMap(AnonymousRoomLastMessage::roomId, Function.identity()));
        Map<Long, Long> unreadCounts = anonymousMessageRepository
                .countAllUnreadByRoomIdIn(roomIds, userId).stream()
                .collect(Collectors.toMap(
                        AnonymousRoomUnreadCount::roomId,
                        AnonymousRoomUnreadCount::unreadMessageCount));
        Map<Long, ProductSummaryResponse> products = productService.findAllProductSummaries(
                pageRooms.stream().map(AnonymousRoomListRow::productId).collect(Collectors.toSet()));
        Map<Long, String> counterpartNames = findCounterpartNames(condition.role(), pageRooms);

        List<AnonymousChatRoomSummaryResponse> items = pageRooms.stream()
                .map(room -> toRoomResponse(
                        userId, condition.role(), room, lastMessages, unreadCounts, products, counterpartNames))
                .toList();
        String nextCursor = hasNext ? AnonymousRoomCursor.from(pageRooms.getLast()).encode() : null;
        return new CursorPageResponse<>(items, nextCursor, hasNext);
    }

    public void createRoom(Long questionerUserId, Long recipientUserId, Long productId) {
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

    private List<AnonymousRoomListRow> findRooms(
            Long userId, AnonymousChatRole role, AnonymousRoomCursor cursor, int limit) {
        LocalDateTime cursorActivityAt = cursor == null ? null : cursor.lastActivityAt();
        Long cursorRoomId = cursor == null ? null : cursor.roomId();
        return switch (role) {
            case ASKER -> anonymousRoomRepository.findAllAskedByUserId(
                    userId, cursorActivityAt, cursorRoomId, Limit.of(limit));
            case RESPONDER -> anonymousRoomRepository.findAllReceivedByUserId(
                    userId, cursorActivityAt, cursorRoomId, Limit.of(limit));
        };
    }

    private Map<Long, String> findCounterpartNames(
            AnonymousChatRole role, List<AnonymousRoomListRow> rooms) {
        if (role == AnonymousChatRole.RESPONDER) {
            return Map.of();
        }
        Collection<Long> recipientUserIds = rooms.stream()
                .map(AnonymousRoomListRow::recipientUserId)
                .collect(Collectors.toSet());
        return userService.findAllActiveUsersByIds(recipientUserIds).stream()
                .collect(Collectors.toMap(UserDetailResponse::id, UserDetailResponse::nickname));
    }

    private AnonymousChatRoomSummaryResponse toRoomResponse(
            Long userId,
            AnonymousChatRole role,
            AnonymousRoomListRow room,
            Map<Long, AnonymousRoomLastMessage> lastMessages,
            Map<Long, Long> unreadCounts,
            Map<Long, ProductSummaryResponse> products,
            Map<Long, String> counterpartNames
    ) {
        long unreadCount = unreadCounts.getOrDefault(room.roomId(), 0L);
        ProductSummaryResponse product = products.get(room.productId());
        String counterpartAlias = role == AnonymousChatRole.ASKER
                ? counterpartNames.get(room.recipientUserId())
                : room.questionerAlias();
        return new AnonymousChatRoomSummaryResponse(
                room.roomId(),
                counterpartAlias,
                unreadCount > 0,
                unreadCount,
                room.lastActivityAt(),
                toLastMessageResponse(userId, lastMessages.get(room.roomId())),
                new AnonymousChatProductResponse(product.productId(), product.name())
        );
    }

    private AnonymousChatLastMessageResponse toLastMessageResponse(
            Long userId, AnonymousRoomLastMessage message) {
        if (message == null) {
            return null;
        }
        return new AnonymousChatLastMessageResponse(
                message.messageId(),
                message.content(),
                message.senderUserId().equals(userId) ? SENDER_ME : SENDER_OTHER,
                message.sentAt().atOffset(ZoneOffset.UTC)
        );
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
