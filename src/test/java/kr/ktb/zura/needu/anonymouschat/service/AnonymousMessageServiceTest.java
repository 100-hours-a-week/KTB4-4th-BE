package kr.ktb.zura.needu.anonymouschat.service;

import kr.ktb.zura.needu.anonymouschat.entity.AnonymousRoom;
import kr.ktb.zura.needu.anonymouschat.exception.AnonymousChatErrorCode;
import kr.ktb.zura.needu.anonymouschat.repository.AnonymousMessageRepository;
import kr.ktb.zura.needu.anonymouschat.repository.AnonymousRoomRepository;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.CommonErrorCode;
import kr.ktb.zura.needu.user.exception.UserErrorCode;
import kr.ktb.zura.needu.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

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

    private void givenParticipatingRoom(Long userId) {
        AnonymousRoom room = new AnonymousRoom(10L, QUESTIONER_ID, RECIPIENT_ID, "익명의 수달");
        ReflectionTestUtils.setField(room, "id", ROOM_ID);
        given(anonymousChatService.findParticipatingRoom(userId, ROOM_ID)).willReturn(room);
    }
}
