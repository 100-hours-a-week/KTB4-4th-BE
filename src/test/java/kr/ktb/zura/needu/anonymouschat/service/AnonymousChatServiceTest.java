package kr.ktb.zura.needu.anonymouschat.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import kr.ktb.zura.needu.anonymouschat.dto.request.AnonymousRoomSearchCondition;
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
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.response.CursorPageResponse;
import kr.ktb.zura.needu.friend.service.FriendService;
import kr.ktb.zura.needu.notification.service.NotificationCreationService;
import kr.ktb.zura.needu.notification.type.NotificationResourceType;
import kr.ktb.zura.needu.notification.type.NotificationType;
import kr.ktb.zura.needu.product.dto.response.ProductSummaryResponse;
import kr.ktb.zura.needu.product.service.ProductService;
import kr.ktb.zura.needu.product.type.ProductCategory;
import kr.ktb.zura.needu.user.dto.response.UserDetailResponse;
import kr.ktb.zura.needu.user.exception.UserErrorCode;
import kr.ktb.zura.needu.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Limit;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AnonymousChatServiceTest {

    private static final Long ROOM_ID = 1L;
    private static final Long PRODUCT_ID = 10L;
    private static final Long QUESTIONER_ID = 100L;
    private static final Long RECIPIENT_ID = 200L;
    private static final Long OUTSIDER_ID = 300L;
    private static final String QUESTIONER_ALIAS = "익명의 수달";
    private static final ProductSummaryResponse PRODUCT = new ProductSummaryResponse(
            PRODUCT_ID, "무선 헤드폰", 129000L, ProductCategory.DIGITAL, "https://example.com/product.png");

    @Mock
    private AnonymousRoomRepository anonymousRoomRepository;

    @Mock
    private AnonymousMessageRepository anonymousMessageRepository;

    @Mock
    private UserService userService;

    @Mock
    private FriendService friendService;

    @Mock
    private ProductService productService;

    @Mock
    private AnonymousAliasGenerator anonymousAliasGenerator;

    @Mock
    private NotificationCreationService notificationCreationService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Spy
    private Duration notificationExpiration = Duration.ofDays(30);

    @Spy
    private Clock clock = Clock.fixed(Instant.parse("2026-10-24T01:00:00Z"), ZoneOffset.UTC);

    @InjectMocks
    private AnonymousChatService anonymousChatService;

    @Test
    void asker_findAllRooms_combinesPageDataAndReturnsNextCursor() {
        LocalDateTime latestAt = LocalDateTime.of(2026, 10, 24, 15, 30);
        AnonymousRoomListRow first = roomRow(3L, 30L, 300L, "익명의 수달", latestAt);
        AnonymousRoomListRow second = roomRow(2L, 20L, 200L, "익명의 여우", latestAt.minusHours(1));
        AnonymousRoomListRow extra = roomRow(1L, 10L, 400L, "익명의 토끼", latestAt.minusHours(2));
        given(anonymousRoomRepository.findAllAskedByUserId(
                QUESTIONER_ID, null, null, Limit.of(3))).willReturn(List.of(first, second, extra));
        given(anonymousMessageRepository.findAllLastMessagesByRoomIdIn(List.of(3L, 2L)))
                .willReturn(List.of(new AnonymousRoomLastMessage(
                        3L, 305L, "이 상품 선물로 괜찮을까요?", 300L, latestAt)));
        given(anonymousMessageRepository.countAllUnreadByRoomIdIn(List.of(3L, 2L), QUESTIONER_ID))
                .willReturn(List.of(new AnonymousRoomUnreadCount(3L, 3L)));
        given(productService.findAllProductSummaries(Set.of(30L, 20L))).willReturn(Map.of(
                30L, product(30L, "무선 헤드폰"),
                20L, product(20L, "니트 가디건")));
        given(userService.findAllActiveUsersByIds(Set.of(300L, 200L))).willReturn(List.of(
                user(300L, "수달"), user(200L, "여우")));

        CursorPageResponse<AnonymousChatRoomSummaryResponse> response = anonymousChatService.findAllRooms(
                QUESTIONER_ID, new AnonymousRoomSearchCondition(AnonymousChatRole.ASKER, null, 2));

        assertThat(response.hasNext()).isTrue();
        assertThat(AnonymousRoomCursor.decode(response.nextCursor()))
                .isEqualTo(AnonymousRoomCursor.from(second));
        assertThat(response.items()).hasSize(2);
        assertThat(response.items().getFirst().counterpartAlias()).isEqualTo("수달");
        assertThat(response.items().getFirst().hasUnreadMessage()).isTrue();
        assertThat(response.items().getFirst().unreadMessageCount()).isEqualTo(3L);
        assertThat(response.items().getFirst().lastActiveAt()).isEqualTo(latestAt);
        assertThat(response.items().getFirst().lastMessage().sender()).isEqualTo("OTHER");
        assertThat(response.items().getFirst().lastMessage().sentAt())
                .isEqualTo(latestAt.atOffset(ZoneOffset.UTC));
        assertThat(response.items().getFirst().product().name()).isEqualTo("무선 헤드폰");
        assertThat(response.items().get(1).lastMessage()).isNull();
    }

    @Test
    void responder_findAllRooms_usesQuestionerAliasWithoutLoadingUsers() {
        LocalDateTime activityAt = LocalDateTime.of(2026, 10, 24, 15, 30);
        AnonymousRoomListRow room = roomRow(1L, PRODUCT_ID, RECIPIENT_ID, QUESTIONER_ALIAS, activityAt);
        given(anonymousRoomRepository.findAllReceivedByUserId(
                RECIPIENT_ID, null, null, Limit.of(21))).willReturn(List.of(room));
        given(anonymousMessageRepository.findAllLastMessagesByRoomIdIn(List.of(ROOM_ID))).willReturn(List.of());
        given(anonymousMessageRepository.countAllUnreadByRoomIdIn(List.of(ROOM_ID), RECIPIENT_ID))
                .willReturn(List.of());
        given(productService.findAllProductSummaries(Set.of(PRODUCT_ID))).willReturn(Map.of(PRODUCT_ID, PRODUCT));

        CursorPageResponse<AnonymousChatRoomSummaryResponse> response = anonymousChatService.findAllRooms(
                RECIPIENT_ID, new AnonymousRoomSearchCondition(AnonymousChatRole.RESPONDER, null, 20));

        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextCursor()).isNull();
        assertThat(response.items().getFirst().counterpartAlias()).isEqualTo(QUESTIONER_ALIAS);
        assertThat(response.items().getFirst().hasUnreadMessage()).isFalse();
        verify(userService, never()).findAllActiveUsersByIds(anyCollection());
    }

    @Test
    void noRooms_findAllRooms_returnsEmptyPageWithoutLoadingRelatedData() {
        given(anonymousRoomRepository.findAllAskedByUserId(
                QUESTIONER_ID, null, null, Limit.of(21))).willReturn(List.of());

        CursorPageResponse<AnonymousChatRoomSummaryResponse> response = anonymousChatService.findAllRooms(
                QUESTIONER_ID, new AnonymousRoomSearchCondition(AnonymousChatRole.ASKER, null, 20));

        assertThat(response).isEqualTo(new CursorPageResponse<>(List.of(), null, false));
        verifyNoInteractions(anonymousMessageRepository, productService);
    }

    @Test
    void validRequest_createRoom_savesRoomWithGeneratedAlias() {
        Set<String> usedAliases = Set.of("마음을 전하는 수달");
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 24, 1, 0);
        given(anonymousRoomRepository.findAllActiveQuestionerAliasesByRecipientUserId(RECIPIENT_ID))
                .willReturn(usedAliases);
        given(anonymousAliasGenerator.generate(usedAliases)).willReturn(QUESTIONER_ALIAS);
        given(anonymousRoomRepository.save(any(AnonymousRoom.class))).willAnswer(invocation -> {
            AnonymousRoom room = invocation.getArgument(0);
            ReflectionTestUtils.setField(room, "id", ROOM_ID);
            ReflectionTestUtils.setField(room, "createdAt", createdAt);
            return room;
        });

        AnonymousRoomCreatedResponse response =
                anonymousChatService.createRoom(QUESTIONER_ID, RECIPIENT_ID, PRODUCT_ID);

        verify(userService).validateActiveUser(QUESTIONER_ID);
        verify(friendService).validateFriend(QUESTIONER_ID, RECIPIENT_ID);
        verify(productService).validateGiftProduct(RECIPIENT_ID, PRODUCT_ID);
        verify(userService).validateActiveUserForUpdate(RECIPIENT_ID);
        ArgumentCaptor<AnonymousRoom> roomCaptor = ArgumentCaptor.forClass(AnonymousRoom.class);
        verify(anonymousRoomRepository).save(roomCaptor.capture());
        verify(notificationCreationService).createNotifications(
                Set.of(RECIPIENT_ID),
                NotificationType.CHAT,
                null,
                "친구가 슬쩍 물어봤어요.",
                "익명대화방에서 친구의 질문을 확인해 보세요.",
                NotificationResourceType.ANONYMOUS_ROOM,
                ROOM_ID,
                "ANONYMOUS_CHAT_ROOM_CREATED:" + ROOM_ID,
                LocalDateTime.of(2026, 11, 23, 1, 0)
        );
        AnonymousRoom savedRoom = roomCaptor.getValue();
        assertThat(savedRoom.getProductId()).isEqualTo(PRODUCT_ID);
        assertThat(savedRoom.getQuestionerUserId()).isEqualTo(QUESTIONER_ID);
        assertThat(savedRoom.getRecipientUserId()).isEqualTo(RECIPIENT_ID);
        assertThat(savedRoom.getQuestionerAlias()).isEqualTo(QUESTIONER_ALIAS);
        assertThat(response.roomId()).isEqualTo(ROOM_ID);
        assertThat(response.createdAt()).isEqualTo(OffsetDateTime.parse("2026-10-24T10:00:00+09:00"));
    }

    @Test
    void sameParticipantsAndProduct_createRoom_returnsLatestRoomWithoutCreatingOrNotifying() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 23, 1, 0);
        AnonymousRoom existingRoom = room();
        ReflectionTestUtils.setField(existingRoom, "createdAt", createdAt);
        given(anonymousRoomRepository
                .findFirstByProductIdAndQuestionerUserIdAndRecipientUserIdOrderByIdDesc(
                        PRODUCT_ID, QUESTIONER_ID, RECIPIENT_ID))
                .willReturn(Optional.of(existingRoom));

        AnonymousRoomCreatedResponse response =
                anonymousChatService.createRoom(QUESTIONER_ID, RECIPIENT_ID, PRODUCT_ID);

        assertThat(response.roomId()).isEqualTo(ROOM_ID);
        assertThat(response.createdAt()).isEqualTo(OffsetDateTime.parse("2026-10-23T10:00:00+09:00"));
        verify(userService).validateActiveUserForUpdate(RECIPIENT_ID);
        verify(anonymousRoomRepository, never()).save(any());
        verifyNoInteractions(anonymousAliasGenerator, notificationCreationService);
    }

    @Test
    void questioner_findRoom_returnsRecipientNicknameAndProduct() {
        givenRoom();
        given(productService.findProductSummary(PRODUCT_ID)).willReturn(PRODUCT);
        given(userService.findUserById(RECIPIENT_ID)).willReturn(Optional.of(recipient()));

        AnonymousRoomDetailResponse response = anonymousChatService.findRoom(QUESTIONER_ID, ROOM_ID);

        verify(userService).validateActiveUser(QUESTIONER_ID);
        assertThat(response.roomId()).isEqualTo(ROOM_ID);
        assertThat(response.askedByMe()).isTrue();
        assertThat(response.targetName()).isEqualTo("지호");
        assertThat(response.product()).isEqualTo(PRODUCT);
    }

    @Test
    void recipient_findRoom_returnsQuestionerAliasInsteadOfName() {
        givenRoom();
        given(productService.findProductSummary(PRODUCT_ID)).willReturn(PRODUCT);

        AnonymousRoomDetailResponse response = anonymousChatService.findRoom(RECIPIENT_ID, ROOM_ID);

        assertThat(response.askedByMe()).isFalse();
        assertThat(response.targetName()).isEqualTo(QUESTIONER_ALIAS);
        verify(userService, never()).findUserById(anyLong());
    }

    @Test
    void recipientWithdrawn_findRoom_returnsNullTargetName() {
        givenRoom();
        given(productService.findProductSummary(PRODUCT_ID)).willReturn(PRODUCT);
        given(userService.findUserById(RECIPIENT_ID)).willReturn(Optional.empty());

        AnonymousRoomDetailResponse response = anonymousChatService.findRoom(QUESTIONER_ID, ROOM_ID);

        assertThat(response.targetName()).isNull();
    }

    @Test
    void missingRoom_findRoom_throwsNotFound() {
        given(anonymousRoomRepository.findById(ROOM_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> anonymousChatService.findRoom(QUESTIONER_ID, ROOM_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AnonymousChatErrorCode.ANONYMOUS_CHAT_ROOM_NOT_FOUND);

        verifyNoInteractions(productService);
    }

    @Test
    void nonParticipant_findRoom_throwsForbiddenWithoutLoadingProduct() {
        givenRoom();

        assertThatThrownBy(() -> anonymousChatService.findRoom(OUTSIDER_ID, ROOM_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AnonymousChatErrorCode.ANONYMOUS_CHAT_ROOM_FORBIDDEN);

        verifyNoInteractions(productService);
    }

    @Test
    void inactiveUser_findRoom_throwsWithoutQueryingRoom() {
        willThrow(new BusinessException(UserErrorCode.USER_ONBOARDING_REQUIRED))
                .given(userService).validateActiveUser(QUESTIONER_ID);

        assertThatThrownBy(() -> anonymousChatService.findRoom(QUESTIONER_ID, ROOM_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.USER_ONBOARDING_REQUIRED);

        verifyNoInteractions(anonymousRoomRepository, productService);
    }

    @Test
    void questioner_leaveRoom_endsRoomAndPublishesPartnerEvent() {
        AnonymousRoom room = givenRoomForUpdate();
        Instant now = Instant.parse("2026-10-28T06:30:00Z");
        given(clock.instant()).willReturn(now);
        given(clock.getZone()).willReturn(ZoneOffset.UTC);

        anonymousChatService.leaveRoom(QUESTIONER_ID, ROOM_ID);

        verify(userService).validateActiveUser(QUESTIONER_ID);
        assertThat(room.isEnded()).isTrue();
        assertThat(room.getEndedByUserId()).isEqualTo(QUESTIONER_ID);
        assertThat(room.getEndedAt()).isEqualTo(LocalDateTime.ofInstant(now, ZoneOffset.UTC));
        verify(eventPublisher).publishEvent(new AnonymousRoomEndedEvent(
                RECIPIENT_ID, ROOM_ID, OffsetDateTime.ofInstant(now, ZoneOffset.UTC)));
    }

    @Test
    void recipient_leaveRoom_publishesEventToQuestioner() {
        givenRoomForUpdate();
        Instant now = Instant.parse("2026-10-28T06:30:00Z");
        given(clock.instant()).willReturn(now);
        given(clock.getZone()).willReturn(ZoneOffset.UTC);

        anonymousChatService.leaveRoom(RECIPIENT_ID, ROOM_ID);

        verify(eventPublisher).publishEvent(new AnonymousRoomEndedEvent(
                QUESTIONER_ID, ROOM_ID, OffsetDateTime.ofInstant(now, ZoneOffset.UTC)));
    }

    @Test
    void alreadyEndedRoom_leaveRoom_doesNotPublishDuplicateEvent() {
        AnonymousRoom room = givenRoomForUpdate();
        LocalDateTime firstEndedAt = LocalDateTime.of(2026, 10, 27, 6, 30);
        room.end(RECIPIENT_ID, firstEndedAt);
        given(clock.instant()).willReturn(Instant.parse("2026-10-28T06:30:00Z"));
        given(clock.getZone()).willReturn(ZoneOffset.UTC);

        anonymousChatService.leaveRoom(QUESTIONER_ID, ROOM_ID);

        assertThat(room.getEndedByUserId()).isEqualTo(RECIPIENT_ID);
        assertThat(room.getEndedAt()).isEqualTo(firstEndedAt);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void nonParticipant_leaveRoom_throwsForbiddenWithoutPublishingEvent() {
        givenRoomForUpdate();

        assertThatThrownBy(() -> anonymousChatService.leaveRoom(OUTSIDER_ID, ROOM_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AnonymousChatErrorCode.ANONYMOUS_CHAT_ROOM_FORBIDDEN);

        verifyNoInteractions(eventPublisher);
    }

    private void givenRoom() {
        given(anonymousRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(room()));
    }

    private AnonymousRoom givenRoomForUpdate() {
        AnonymousRoom room = room();
        given(anonymousRoomRepository.findByIdForUpdate(ROOM_ID)).willReturn(Optional.of(room));
        return room;
    }

    private AnonymousRoom room() {
        AnonymousRoom room = new AnonymousRoom(PRODUCT_ID, QUESTIONER_ID, RECIPIENT_ID, QUESTIONER_ALIAS);
        ReflectionTestUtils.setField(room, "id", ROOM_ID);
        return room;
    }

    private UserDetailResponse recipient() {
        return new UserDetailResponse(RECIPIENT_ID, "지호", "https://image.test/profile.png", true,
                LocalDate.of(2000, 1, 1));
    }

    private AnonymousRoomListRow roomRow(
            Long roomId, Long productId, Long recipientUserId, String alias, LocalDateTime lastActivityAt) {
        return new AnonymousRoomListRow(roomId, productId, recipientUserId, alias, lastActivityAt);
    }

    private ProductSummaryResponse product(Long productId, String name) {
        return new ProductSummaryResponse(productId, name, null, ProductCategory.DIGITAL, null);
    }

    private UserDetailResponse user(Long userId, String nickname) {
        return new UserDetailResponse(userId, nickname, null, true, LocalDate.of(2000, 1, 1));
    }
}
