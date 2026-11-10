package kr.ktb.zura.needu.anonymouschat.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
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
import kr.ktb.zura.needu.anonymouschat.dto.response.AnonymousCounterpartProfileResponse;
import kr.ktb.zura.needu.anonymouschat.dto.response.AnonymousRoomCreatedResponse;
import kr.ktb.zura.needu.anonymouschat.dto.response.AnonymousRoomDetailResponse;
import kr.ktb.zura.needu.anonymouschat.entity.AnonymousRoom;
import kr.ktb.zura.needu.anonymouschat.exception.AnonymousChatErrorCode;
import kr.ktb.zura.needu.anonymouschat.repository.AnonymousMessageRepository;
import kr.ktb.zura.needu.anonymouschat.repository.AnonymousRoomLastMessage;
import kr.ktb.zura.needu.anonymouschat.repository.AnonymousRoomListRow;
import kr.ktb.zura.needu.anonymouschat.repository.AnonymousRoomRepository;
import kr.ktb.zura.needu.anonymouschat.repository.AnonymousRoomUnreadCount;
import kr.ktb.zura.needu.anonymouschat.support.AnonymousAlias;
import kr.ktb.zura.needu.anonymouschat.support.AnonymousAliasGenerator;
import kr.ktb.zura.needu.anonymouschat.support.AnonymousChatTime;
import kr.ktb.zura.needu.anonymouschat.type.AnonymousChatRole;
import kr.ktb.zura.needu.anonymouschat.type.AnonymousRoomEndedReason;
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
        Map<Long, UserDetailResponse> recipients = findRecipients(condition.role(), pageRooms);

        List<AnonymousChatRoomSummaryResponse> items = pageRooms.stream()
                .map(room -> toRoomResponse(
                        userId, condition.role(), room, lastMessages, unreadCounts, products, recipients))
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
        AnonymousAlias questionerAlias = anonymousAliasGenerator.generate(usedAliases);
        AnonymousRoom room = anonymousRoomRepository.save(new AnonymousRoom(
                productId,
                questionerUserId,
                recipientUserId,
                questionerAlias.name(),
                questionerAlias.avatarAnimal(),
                questionerAlias.avatarColor()));
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
        UserDetailResponse recipient = askedByMe
                ? userService.findUserById(room.getRecipientUserId()).orElse(null)
                : null;

        return AnonymousRoomDetailResponse.of(
                room,
                askedByMe,
                askedByMe ? findNickname(recipient) : room.getQuestionerAlias(),
                toCounterpartProfile(
                        askedByMe, recipient, room.getQuestionerAvatarAnimal(), room.getQuestionerAvatarColor()),
                productService.findProductSummary(room.getProductId())
        );
    }

    @Transactional
    public void leaveRoom(Long userId, Long roomId) {
        userService.validateActiveUser(userId);
        AnonymousRoom room = findParticipatingRoomForUpdate(userId, roomId);
        LocalDateTime leftAt = LocalDateTime.now(clock);
        // 상대가 이미 나간 방이면 알릴 대상이 없다
        boolean partnerJoined = room.isPartnerJoined(userId);
        room.leave(userId, leftAt);
        if (partnerJoined) {
            eventPublisher.publishEvent(new AnonymousRoomEndedEvent(
                    room.getPartnerUserId(userId), room.getId(), room.getStatus(),
                    AnonymousRoomEndedReason.PARTNER_LEFT, AnonymousChatTime.toKst(leftAt)));
        }
    }

    @Transactional
    public void endAllActiveRoomsByBlockedUser(Long userId) {
        LocalDateTime endedAt = LocalDateTime.now(clock);
        anonymousRoomRepository.findAllParticipatingByUserIdAndStatusForUpdate(userId, AnonymousRoomStatus.ACTIVE)
                .forEach(room -> {
                    room.end(userId, endedAt);
                    eventPublisher.publishEvent(new AnonymousRoomEndedEvent(
                            room.getPartnerUserId(userId), room.getId(), room.getStatus(),
                            AnonymousRoomEndedReason.USER_BLOCKED, AnonymousChatTime.toKst(endedAt)));
                });
    }

    public AnonymousRoom findParticipatingRoom(Long userId, Long roomId) {
        AnonymousRoom room = anonymousRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(AnonymousChatErrorCode.ANONYMOUS_CHAT_ROOM_NOT_FOUND));
        validateAccessibleRoom(room, userId);
        return room;
    }

    public void validatePartnerJoined(AnonymousRoom room, Long userId) {
        if (!room.isPartnerJoined(userId)) {
            throw new BusinessException(AnonymousChatErrorCode.ANONYMOUS_CHAT_PARTNER_LEFT);
        }
    }

    private AnonymousRoom findParticipatingRoomForUpdate(Long userId, Long roomId) {
        AnonymousRoom room = anonymousRoomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new BusinessException(AnonymousChatErrorCode.ANONYMOUS_CHAT_ROOM_NOT_FOUND));
        validateAccessibleRoom(room, userId);
        return room;
    }

    private void validateAccessibleRoom(AnonymousRoom room, Long userId) {
        if (!room.isParticipant(userId)) {
            throw new BusinessException(AnonymousChatErrorCode.ANONYMOUS_CHAT_ROOM_FORBIDDEN);
        }
        if (room.isEnded()) {
            throw new BusinessException(AnonymousChatErrorCode.ANONYMOUS_CHAT_ROOM_ENDED);
        }
        if (!room.isJoined(userId)) {
            throw new BusinessException(AnonymousChatErrorCode.ANONYMOUS_CHAT_PARTICIPANT_LEFT);
        }
    }

    private List<AnonymousRoomListRow> findRooms(
            Long userId, AnonymousChatRole role, AnonymousRoomCursor cursor, int limit) {
        LocalDateTime cursorActivityAt = cursor == null ? null : cursor.lastActivityAt();
        Long cursorRoomId = cursor == null ? null : cursor.roomId();
        return switch (role) {
            case ASKER -> anonymousRoomRepository.findAllVisibleAskedByUserId(
                    userId, cursorActivityAt, cursorRoomId, Limit.of(limit));
            case RESPONDER -> anonymousRoomRepository.findAllVisibleReceivedByUserId(
                    userId, cursorActivityAt, cursorRoomId, Limit.of(limit));
        };
    }

    private Map<Long, UserDetailResponse> findRecipients(
            AnonymousChatRole role, List<AnonymousRoomListRow> rooms) {
        if (role == AnonymousChatRole.RESPONDER) {
            return Map.of();
        }
        Collection<Long> recipientUserIds = rooms.stream()
                .map(AnonymousRoomListRow::recipientUserId)
                .collect(Collectors.toSet());
        return userService.findAllActiveUsersByIds(recipientUserIds).stream()
                .collect(Collectors.toMap(UserDetailResponse::id, Function.identity()));
    }

    private AnonymousChatRoomSummaryResponse toRoomResponse(
            Long userId,
            AnonymousChatRole role,
            AnonymousRoomListRow room,
            Map<Long, AnonymousRoomLastMessage> lastMessages,
            Map<Long, Long> unreadCounts,
            Map<Long, ProductSummaryResponse> products,
            Map<Long, UserDetailResponse> recipients
    ) {
        long unreadCount = unreadCounts.getOrDefault(room.roomId(), 0L);
        ProductSummaryResponse product = products.get(room.productId());
        boolean askedByMe = role == AnonymousChatRole.ASKER;
        UserDetailResponse recipient = recipients.get(room.recipientUserId());
        return new AnonymousChatRoomSummaryResponse(
                room.roomId(),
                askedByMe ? findNickname(recipient) : room.questionerAlias(),
                toCounterpartProfile(
                        askedByMe, recipient, room.questionerAvatarAnimal(), room.questionerAvatarColor()),
                unreadCount > 0,
                unreadCount,
                AnonymousChatTime.toKst(room.lastActivityAt()),
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
                AnonymousChatTime.toKst(message.sentAt())
        );
    }

    // 내가 질문한 방이면 상대는 실명 사용자, 질문받은 방이면 상대는 익명 질문자다. 목록·상세가 같은 규칙을 쓴다
    private static AnonymousCounterpartProfileResponse toCounterpartProfile(
            boolean askedByMe, UserDetailResponse recipient, String avatarAnimal, String avatarColor) {
        if (!askedByMe) {
            return AnonymousCounterpartProfileResponse.ofAnonymous(avatarAnimal, avatarColor);
        }
        return AnonymousCounterpartProfileResponse.ofUser(recipient == null ? null : recipient.profileImageUrl());
    }

    private static String findNickname(UserDetailResponse user) {
        return user == null ? null : user.nickname();
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
