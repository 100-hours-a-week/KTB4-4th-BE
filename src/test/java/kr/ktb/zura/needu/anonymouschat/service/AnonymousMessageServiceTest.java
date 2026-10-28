package kr.ktb.zura.needu.anonymouschat.service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import kr.ktb.zura.needu.anonymouschat.dto.request.AnonymousMessageSearchCondition;
import kr.ktb.zura.needu.anonymouschat.dto.response.AnonymousMessageListResponse;
import kr.ktb.zura.needu.anonymouschat.dto.response.AnonymousMessageSummaryResponse;
import kr.ktb.zura.needu.anonymouschat.entity.AnonymousMessage;
import kr.ktb.zura.needu.anonymouschat.entity.AnonymousRoom;
import kr.ktb.zura.needu.anonymouschat.exception.AnonymousChatErrorCode;
import kr.ktb.zura.needu.anonymouschat.repository.AnonymousMessageRepository;
import kr.ktb.zura.needu.anonymouschat.repository.AnonymousRoomRepository;
import kr.ktb.zura.needu.anonymouschat.type.AnonymousMessageSender;
import kr.ktb.zura.needu.anonymouschat.type.AnonymousRoomEndedReason;
import kr.ktb.zura.needu.anonymouschat.type.AnonymousRoomStatus;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.CommonErrorCode;
import kr.ktb.zura.needu.common.response.CursorPageResponse;
import kr.ktb.zura.needu.user.exception.UserErrorCode;
import kr.ktb.zura.needu.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AnonymousMessageServiceTest {

    private static final Long ROOM_ID = 1L;
    private static final Long QUESTIONER_ID = 100L;
    private static final Long RECIPIENT_ID = 200L;
    private static final Long MESSAGE_ID = 501L;

    @Mock
    private AnonymousChatService anonymousChatService;

    @Mock
    private AnonymousRoomRepository anonymousRoomRepository;

    @Mock
    private AnonymousMessageRepository anonymousMessageRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private AnonymousMessageService anonymousMessageService;

    @Test
    void questioner_readMessages_updatesQuestionerReadPosition() {
        givenParticipatingRoom(QUESTIONER_ID);
        given(anonymousMessageRepository.existsByIdAndRoomId(MESSAGE_ID, ROOM_ID)).willReturn(true);

        anonymousMessageService.readMessages(QUESTIONER_ID, ROOM_ID, MESSAGE_ID);

        verify(userService).validateActiveUser(QUESTIONER_ID);
        verify(anonymousRoomRepository).updateQuestionerLastReadMessageId(ROOM_ID, MESSAGE_ID);
        verify(anonymousRoomRepository, never()).updateRecipientLastReadMessageId(anyLong(), anyLong());
    }

    @Test
    void recipient_readMessages_updatesRecipientReadPosition() {
        givenParticipatingRoom(RECIPIENT_ID);
        given(anonymousMessageRepository.existsByIdAndRoomId(MESSAGE_ID, ROOM_ID)).willReturn(true);

        anonymousMessageService.readMessages(RECIPIENT_ID, ROOM_ID, MESSAGE_ID);

        verify(anonymousRoomRepository).updateRecipientLastReadMessageId(ROOM_ID, MESSAGE_ID);
        verify(anonymousRoomRepository, never()).updateQuestionerLastReadMessageId(anyLong(), anyLong());
    }

    @Test
    void messageNotInRoom_readMessages_throwsInvalidInputWithoutUpdating() {
        givenParticipatingRoom(QUESTIONER_ID);
        given(anonymousMessageRepository.existsByIdAndRoomId(MESSAGE_ID, ROOM_ID)).willReturn(false);

        assertThatThrownBy(() -> anonymousMessageService.readMessages(QUESTIONER_ID, ROOM_ID, MESSAGE_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.COMMON_INVALID_INPUT);

        verifyNoInteractions(anonymousRoomRepository);
    }

    @Test
    void nonParticipant_readMessages_throwsForbiddenWithoutUpdating() {
        given(anonymousChatService.findParticipatingRoom(QUESTIONER_ID, ROOM_ID))
                .willThrow(new BusinessException(AnonymousChatErrorCode.ANONYMOUS_CHAT_ROOM_FORBIDDEN));

        assertThatThrownBy(() -> anonymousMessageService.readMessages(QUESTIONER_ID, ROOM_ID, MESSAGE_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AnonymousChatErrorCode.ANONYMOUS_CHAT_ROOM_FORBIDDEN);

        verifyNoInteractions(anonymousMessageRepository, anonymousRoomRepository);
    }

    @Test
    void inactiveUser_readMessages_throwsWithoutQueryingRoom() {
        willThrow(new BusinessException(UserErrorCode.USER_BLOCKED))
                .given(userService).validateActiveUser(QUESTIONER_ID);

        assertThatThrownBy(() -> anonymousMessageService.readMessages(QUESTIONER_ID, ROOM_ID, MESSAGE_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.USER_BLOCKED);

        verifyNoInteractions(anonymousChatService, anonymousMessageRepository, anonymousRoomRepository);
    }

    @Test
    void moreMessagesThanSize_findAllMessages_returnsPageWithNextCursor() {
        AnonymousRoom room = givenParticipatingRoom(QUESTIONER_ID);
        given(anonymousMessageRepository.findAllLatestByRoomId(ROOM_ID, null, Limit.of(3))).willReturn(List.of(
                message(room, 103L, QUESTIONER_ID),
                message(room, 102L, RECIPIENT_ID),
                message(room, 101L, QUESTIONER_ID)));

        CursorPageResponse<AnonymousMessageSummaryResponse> page = findMessagePage(
                QUESTIONER_ID, ROOM_ID, new AnonymousMessageSearchCondition(null, 2));

        verify(userService).validateActiveUser(QUESTIONER_ID);
        assertThat(page.items()).extracting(AnonymousMessageSummaryResponse::messageId).containsExactly(103L, 102L);
        assertThat(page.items()).extracting(AnonymousMessageSummaryResponse::sender)
                .containsExactly(AnonymousMessageSender.ME, AnonymousMessageSender.OTHER);
        assertThat(page.hasNext()).isTrue();
        assertThat(AnonymousMessageCursor.decode(page.nextCursor()).messageId()).isEqualTo(102L);
    }

    @Test
    void lastPage_findAllMessages_returnsWithoutNextCursor() {
        AnonymousRoom room = givenParticipatingRoom(QUESTIONER_ID);
        given(anonymousMessageRepository.findAllLatestByRoomId(ROOM_ID, null, Limit.of(3)))
                .willReturn(List.of(message(room, 101L, QUESTIONER_ID)));

        CursorPageResponse<AnonymousMessageSummaryResponse> page = findMessagePage(
                QUESTIONER_ID, ROOM_ID, new AnonymousMessageSearchCondition(null, 2));

        assertThat(page.items()).hasSize(1);
        assertThat(page.hasNext()).isFalse();
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    void cursorGiven_findAllMessages_queriesMessagesBeforeCursor() {
        givenParticipatingRoom(QUESTIONER_ID);
        String cursor = new AnonymousMessageCursor(101L).encode();
        given(anonymousMessageRepository.findAllLatestByRoomId(ROOM_ID, 101L, Limit.of(21))).willReturn(List.of());

        CursorPageResponse<AnonymousMessageSummaryResponse> page = findMessagePage(
                QUESTIONER_ID, ROOM_ID, new AnonymousMessageSearchCondition(cursor, 20));

        assertThat(page.items()).isEmpty();
        assertThat(page.hasNext()).isFalse();
    }

    @Test
    void partnerReadPosition_findAllMessages_countsOnlyMyMessagesAfterItAsUnread() {
        AnonymousRoom room = givenParticipatingRoom(RECIPIENT_ID);
        ReflectionTestUtils.setField(room, "questionerLastReadMessageId", 102L);
        given(anonymousMessageRepository.findAllLatestByRoomId(ROOM_ID, null, Limit.of(21))).willReturn(List.of(
                message(room, 104L, RECIPIENT_ID),
                message(room, 103L, QUESTIONER_ID),
                message(room, 102L, RECIPIENT_ID)));

        CursorPageResponse<AnonymousMessageSummaryResponse> page = findMessagePage(
                RECIPIENT_ID, ROOM_ID, new AnonymousMessageSearchCondition(null, 20));

        assertThat(page.items()).extracting(AnonymousMessageSummaryResponse::unreadCount)
                .containsExactly(1, 0, 0);
    }

    @Test
    void partnerNeverRead_findAllMessages_countsAllMyMessagesAsUnread() {
        AnonymousRoom room = givenParticipatingRoom(QUESTIONER_ID);
        given(anonymousMessageRepository.findAllLatestByRoomId(ROOM_ID, null, Limit.of(21))).willReturn(List.of(
                message(room, 102L, RECIPIENT_ID),
                message(room, 101L, QUESTIONER_ID)));

        CursorPageResponse<AnonymousMessageSummaryResponse> page = findMessagePage(
                QUESTIONER_ID, ROOM_ID, new AnonymousMessageSearchCondition(null, 20));

        assertThat(page.items()).extracting(AnonymousMessageSummaryResponse::unreadCount)
                .containsExactly(0, 1);
    }

    @Test
    void malformedCursor_findAllMessages_throwsInvalidRequest() {
        givenParticipatingRoom(QUESTIONER_ID);

        assertThatThrownBy(() -> anonymousMessageService.findAllMessages(
                QUESTIONER_ID, ROOM_ID, new AnonymousMessageSearchCondition("not-base64!!", 20)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.COMMON_INVALID_REQUEST);

        verifyNoInteractions(anonymousMessageRepository);
    }

    @Test
    void nonParticipant_findAllMessages_throwsForbiddenWithoutQueryingMessages() {
        given(anonymousChatService.findParticipatingRoom(QUESTIONER_ID, ROOM_ID))
                .willThrow(new BusinessException(AnonymousChatErrorCode.ANONYMOUS_CHAT_ROOM_FORBIDDEN));

        assertThatThrownBy(() -> anonymousMessageService.findAllMessages(
                QUESTIONER_ID, ROOM_ID, new AnonymousMessageSearchCondition(null, 20)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AnonymousChatErrorCode.ANONYMOUS_CHAT_ROOM_FORBIDDEN);

        verifyNoInteractions(anonymousMessageRepository);
    }

    @Test
    void inactiveUser_findAllMessages_throwsWithoutQueryingRoom() {
        willThrow(new BusinessException(UserErrorCode.USER_BLOCKED))
                .given(userService).validateActiveUser(QUESTIONER_ID);

        assertThatThrownBy(() -> anonymousMessageService.findAllMessages(
                QUESTIONER_ID, ROOM_ID, new AnonymousMessageSearchCondition(null, 20)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.USER_BLOCKED);

        verifyNoInteractions(anonymousChatService, anonymousMessageRepository);
    }

    @Test
    void recipient_findAllMessages_returnsTargetNameForReceivedRoom() {
        AnonymousRoom room = givenParticipatingRoom(RECIPIENT_ID);
        given(anonymousChatService.findTargetName(room, false)).willReturn("익명의 수달");
        given(anonymousMessageRepository.findAllLatestByRoomId(ROOM_ID, null, Limit.of(21))).willReturn(List.of());

        AnonymousMessageListResponse response = anonymousMessageService.findAllMessages(
                RECIPIENT_ID, ROOM_ID, new AnonymousMessageSearchCondition(null, 20));

        assertThat(response.targetName()).isEqualTo("익명의 수달");
    }

    @Test
    void questioner_findAllMessages_returnsTargetNameForAskedRoom() {
        AnonymousRoom room = givenParticipatingRoom(QUESTIONER_ID);
        given(anonymousChatService.findTargetName(room, true)).willReturn("지호");
        given(anonymousMessageRepository.findAllLatestByRoomId(ROOM_ID, null, Limit.of(21))).willReturn(List.of());

        AnonymousMessageListResponse response = anonymousMessageService.findAllMessages(
                QUESTIONER_ID, ROOM_ID, new AnonymousMessageSearchCondition(null, 20));

        assertThat(response.targetName()).isEqualTo("지호");
    }

    @Test
    void activeRoom_findAllMessages_returnsActiveStatusWithoutEndInfo() {
        givenParticipatingRoom(QUESTIONER_ID);
        given(anonymousMessageRepository.findAllLatestByRoomId(ROOM_ID, null, Limit.of(21))).willReturn(List.of());

        AnonymousMessageListResponse response = anonymousMessageService.findAllMessages(
                QUESTIONER_ID, ROOM_ID, new AnonymousMessageSearchCondition(null, 20));

        assertThat(response.roomStatus()).isEqualTo(AnonymousRoomStatus.ACTIVE);
        assertThat(response.endedReason()).isNull();
        assertThat(response.endedAt()).isNull();
    }

    @Test
    void roomEndedByMe_findAllMessages_returnsMeLeftWithEndedAt() {
        AnonymousRoom room = givenParticipatingRoom(QUESTIONER_ID);
        room.end(QUESTIONER_ID, LocalDateTime.of(2026, 10, 25, 8, 30));
        given(anonymousMessageRepository.findAllLatestByRoomId(ROOM_ID, null, Limit.of(21))).willReturn(List.of());

        AnonymousMessageListResponse response = anonymousMessageService.findAllMessages(
                QUESTIONER_ID, ROOM_ID, new AnonymousMessageSearchCondition(null, 20));

        assertThat(response.roomStatus()).isEqualTo(AnonymousRoomStatus.ENDED);
        assertThat(response.endedReason()).isEqualTo(AnonymousRoomEndedReason.ME_LEFT);
        assertThat(response.endedAt()).isEqualTo(OffsetDateTime.parse("2026-10-25T08:30:00Z"));
    }

    @Test
    void roomEndedByPartner_findAllMessages_returnsPartnerLeft() {
        AnonymousRoom room = givenParticipatingRoom(RECIPIENT_ID);
        room.end(QUESTIONER_ID, LocalDateTime.of(2026, 10, 25, 8, 30));
        given(anonymousMessageRepository.findAllLatestByRoomId(ROOM_ID, null, Limit.of(21))).willReturn(List.of());

        AnonymousMessageListResponse response = anonymousMessageService.findAllMessages(
                RECIPIENT_ID, ROOM_ID, new AnonymousMessageSearchCondition(null, 20));

        assertThat(response.roomStatus()).isEqualTo(AnonymousRoomStatus.ENDED);
        assertThat(response.endedReason()).isEqualTo(AnonymousRoomEndedReason.PARTNER_LEFT);
    }

    private CursorPageResponse<AnonymousMessageSummaryResponse> findMessagePage(
            Long userId, Long roomId, AnonymousMessageSearchCondition condition) {
        return anonymousMessageService.findAllMessages(userId, roomId, condition).page();
    }

    private AnonymousRoom givenParticipatingRoom(Long userId) {
        AnonymousRoom room = new AnonymousRoom(10L, QUESTIONER_ID, RECIPIENT_ID, "익명의 수달");
        ReflectionTestUtils.setField(room, "id", ROOM_ID);
        given(anonymousChatService.findParticipatingRoom(userId, ROOM_ID)).willReturn(room);
        return room;
    }

    private static AnonymousMessage message(AnonymousRoom room, Long messageId, Long senderUserId) {
        AnonymousMessage message = new AnonymousMessage(room, senderUserId, UUID.randomUUID(), "메시지 " + messageId);
        ReflectionTestUtils.setField(message, "id", messageId);
        ReflectionTestUtils.setField(message, "createdAt", LocalDateTime.of(2026, 10, 24, 1, 0));
        return message;
    }
}
