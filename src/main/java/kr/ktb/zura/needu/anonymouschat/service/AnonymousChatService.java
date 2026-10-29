package kr.ktb.zura.needu.anonymouschat.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import kr.ktb.zura.needu.anonymouschat.dto.request.AnonymousRoomSearchCondition;
import kr.ktb.zura.needu.anonymouschat.dto.response.AnonymousChatLastMessageResponse;
import kr.ktb.zura.needu.anonymouschat.dto.response.AnonymousChatProductResponse;
import kr.ktb.zura.needu.anonymouschat.dto.response.AnonymousChatRoomSummaryResponse;
import kr.ktb.zura.needu.anonymouschat.dto.response.AnonymousRoomCreatedResponse;
import kr.ktb.zura.needu.anonymouschat.dto.response.AnonymousRoomDetailResponse;
import kr.ktb.zura.needu.anonymouschat.entity.AnonymousRoom;
import kr.ktb.zura.needu.anonymouschat.exception.AnonymousChatErrorCode;
import kr.ktb.zura.needu.anonymouschat.repository.AnonymousMessageRepository;
import kr.ktb.zura.needu.anonymouschat.repository.AnonymousRoomLastMessage;
import kr.ktb.zura.needu.anonymouschat.repository.AnonymousRoomListRow;
import kr.ktb.zura.needu.anonymouschat.repository.AnonymousRoomRepository;
import kr.ktb.zura.needu.anonymouschat.repository.AnonymousRoomUnreadCount;
import kr.ktb.zura.needu.anonymouschat.support.AnonymousAliasGenerator;
import kr.ktb.zura.needu.anonymouschat.type.AnonymousChatRole;
import kr.ktb.zura.needu.anonymouschat.type.AnonymousRoomStatus;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.response.CursorPageResponse;
import kr.ktb.zura.needu.friend.service.FriendService;
import kr.ktb.zura.needu.notification.service.NotificationCreationService;
import kr.ktb.zura.needu.notification.type.NotificationResourceType;
import kr.ktb.zura.needu.notification.type.NotificationType;
import kr.ktb.zura.needu.product.dto.response.ProductSummaryResponse;
import kr.ktb.zura.needu.product.service.ProductService;
import kr.ktb.zura.needu.user.dto.response.UserDetailResponse;
import kr.ktb.zura.needu.user.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnonymousChatService {

    private static final String SENDER_ME = "ME";
    private static final String SENDER_OTHER = "OTHER";
    private static final String ROOM_CREATED_NOTIFICATION_TITLE = "친구가 슬쩍 물어봤어요.";
    private static final String ROOM_CREATED_NOTIFICATION_BODY = "익명대화방에서 친구의 질문을 확인해 보세요.";
    private static final String ROOM_CREATED_NOTIFICATION_DEDUP_KEY_PREFIX = "ANONYMOUS_CHAT_ROOM_CREATED:";

    private final AnonymousRoomRepository anonymousRoomRepository;
    private final AnonymousMessageRepository anonymousMessageRepository;
    private final UserService userService;
    private final FriendService friendService;
    private final ProductService productService;
    private final AnonymousAliasGenerator anonymousAliasGenerator;
    private final NotificationCreationService notificationCreationService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final Duration notificationExpiration;

    public AnonymousChatService(
            AnonymousRoomRepository anonymousRoomRepository,
            AnonymousMessageRepository anonymousMessageRepository,
            UserService userService,
            FriendService friendService,
            ProductService productService,
            AnonymousAliasGenerator anonymousAliasGenerator,
            NotificationCreationService notificationCreationService,
            ApplicationEventPublisher eventPublisher,
            Clock clock,
            @Value("${needu.anonymous-chat.notification-expiration}") Duration notificationExpiration
    ) {
        this.anonymousRoomRepository = anonymousRoomRepository;
        this.anonymousMessageRepository = anonymousMessageRepository;
        this.userService = userService;
        this.friendService = friendService;
        this.productService = productService;
        this.anonymousAliasGenerator = anonymousAliasGenerator;
        this.notificationCreationService = notificationCreationService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.notificationExpiration = notificationExpiration;
    }

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

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AnonymousRoomCreatedResponse createRoom(Long questionerUserId, Long recipientUserId, Long productId) {
        userService.validateActiveUser(questionerUserId);
        friendService.validateFriend(questionerUserId, recipientUserId);
        productService.validateGiftProduct(recipientUserId, productId);
        userService.validateActiveUserForUpdate(recipientUserId);
        AnonymousRoom existingRoom = anonymousRoomRepository
                .findFirstByProductIdAndQuestionerUserIdAndRecipientUserIdAndStatusAndExpiresAtIsNullOrderByIdDesc(
                        productId, questionerUserId, recipientUserId, AnonymousRoomStatus.ACTIVE)
                .orElse(null);
        if (existingRoom != null) {
            return AnonymousRoomCreatedResponse.from(existingRoom);
        }
        Set<String> usedAliases = anonymousRoomRepository
                .findAllActiveQuestionerAliasesByRecipientUserId(recipientUserId);
        String questionerAlias = anonymousAliasGenerator.generate(usedAliases);
        AnonymousRoom room = anonymousRoomRepository.save(
                new AnonymousRoom(productId, questionerUserId, recipientUserId, questionerAlias));
        notificationCreationService.createNotifications(
                Set.of(recipientUserId),
                NotificationType.CHAT,
                null,
                ROOM_CREATED_NOTIFICATION_TITLE,
                ROOM_CREATED_NOTIFICATION_BODY,
                NotificationResourceType.ANONYMOUS_ROOM,
                room.getId(),
                ROOM_CREATED_NOTIFICATION_DEDUP_KEY_PREFIX + room.getId(),
                LocalDateTime.now(clock).plus(notificationExpiration)
        );
        return AnonymousRoomCreatedResponse.from(room);
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

    @Transactional
    public void leaveRoom(Long userId, Long roomId) {
        userService.validateActiveUser(userId);
        AnonymousRoom room = findParticipatingRoomForUpdate(userId, roomId);
        LocalDateTime endedAt = LocalDateTime.now(clock);

        if (!room.end(userId, endedAt)) {
            return;
        }

        Long partnerUserId = room.isQuestioner(userId)
                ? room.getRecipientUserId()
                : room.getQuestionerUserId();
        eventPublisher.publishEvent(new AnonymousRoomEndedEvent(
                partnerUserId, roomId, endedAt.atOffset(ZoneOffset.UTC)));
    }

    public AnonymousRoom findParticipatingRoom(Long userId, Long roomId) {
        AnonymousRoom room = anonymousRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(AnonymousChatErrorCode.ANONYMOUS_CHAT_ROOM_NOT_FOUND));
        if (!room.isParticipant(userId)) {
            throw new BusinessException(AnonymousChatErrorCode.ANONYMOUS_CHAT_ROOM_FORBIDDEN);
        }
        return room;
    }

    private AnonymousRoom findParticipatingRoomForUpdate(Long userId, Long roomId) {
        AnonymousRoom room = anonymousRoomRepository.findByIdForUpdate(roomId)
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

    public String findTargetName(AnonymousRoom room, boolean askedByMe) {
        if (!askedByMe) {
            return room.getQuestionerAlias();
        }
        return userService.findUserById(room.getRecipientUserId())
                .map(UserDetailResponse::nickname)
                .orElse(null);
    }
}
