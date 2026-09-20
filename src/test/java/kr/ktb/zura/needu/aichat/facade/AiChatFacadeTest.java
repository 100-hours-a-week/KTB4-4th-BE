package kr.ktb.zura.needu.aichat.facade;

import java.time.LocalDateTime;

import kr.ktb.zura.needu.aichat.client.AiChatClient;
import kr.ktb.zura.needu.aichat.dto.response.AiServerStartSessionResponse;
import kr.ktb.zura.needu.aichat.entity.AiChatRoom;
import kr.ktb.zura.needu.aichat.exception.AiChatErrorCode;
import kr.ktb.zura.needu.aichat.service.AiChatRoomService;
import kr.ktb.zura.needu.aichat.type.AiChatRoomStatus;
import kr.ktb.zura.needu.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AiChatFacadeTest {

    private static final Long USER_ID = 1L;
    private static final Long ROOM_ID = 101L;
    private static final Long NEW_ROOM_ID = 102L;
    private static final String GREETING = "안녕하세요";

    @Mock
    private AiChatRoomService aiChatRoomService;

    @Mock
    private AiChatClient aiChatClient;

    @InjectMocks
    private AiChatFacade aiChatFacade;

    @Test
    void noRoom_startOrResumeConversation_startsSessionAndReturnsCreated() {
        given(aiChatRoomService.findOrReserveRoom(USER_ID)).willReturn(room(ROOM_ID, AiChatRoomStatus.PENDING, null));
        given(aiChatClient.startSession(USER_ID, ROOM_ID)).willReturn(startSessionResponse(ROOM_ID));
        given(aiChatRoomService.activateRoom(ROOM_ID, GREETING))
                .willReturn(room(ROOM_ID, AiChatRoomStatus.ACTIVE, LocalDateTime.now().plusMinutes(29)));

        AiConversationStartResult result = aiChatFacade.startOrResumeConversation(USER_ID);

        assertThat(result.isCreated()).isTrue();
        assertThat(result.conversation().conversationId()).isEqualTo(ROOM_ID);
        assertThat(result.conversation().status()).isEqualTo(AiChatRoomStatus.ACTIVE);
    }

    @Test
    void activeRoomBeforePurgeAt_startOrResumeConversation_returnsResumedWithoutCallingAiServer() {
        given(aiChatRoomService.findOrReserveRoom(USER_ID))
                .willReturn(room(ROOM_ID, AiChatRoomStatus.ACTIVE, LocalDateTime.now().plusMinutes(10)));

        AiConversationStartResult result = aiChatFacade.startOrResumeConversation(USER_ID);

        assertThat(result.isCreated()).isFalse();
        assertThat(result.conversation().conversationId()).isEqualTo(ROOM_ID);
        verifyNoInteractions(aiChatClient);
    }

    @Test
    void activeRoomPastPurgeAt_startOrResumeConversation_expiresRoomAndCreatesNewConversation() {
        given(aiChatRoomService.findOrReserveRoom(USER_ID))
                .willReturn(room(ROOM_ID, AiChatRoomStatus.ACTIVE, LocalDateTime.now().minusMinutes(1)));
        given(aiChatRoomService.expireAndReserveRoom(USER_ID, ROOM_ID))
                .willReturn(room(NEW_ROOM_ID, AiChatRoomStatus.PENDING, null));
        given(aiChatClient.startSession(USER_ID, NEW_ROOM_ID)).willReturn(startSessionResponse(NEW_ROOM_ID));
        given(aiChatRoomService.activateRoom(NEW_ROOM_ID, GREETING))
                .willReturn(room(NEW_ROOM_ID, AiChatRoomStatus.ACTIVE, LocalDateTime.now().plusMinutes(29)));

        AiConversationStartResult result = aiChatFacade.startOrResumeConversation(USER_ID);

        assertThat(result.isCreated()).isTrue();
        assertThat(result.conversation().conversationId()).isEqualTo(NEW_ROOM_ID);
    }

    @Test
    void analyzingRoomPastPurgeAt_startOrResumeConversation_returnsResumedWithoutCallingAiServer() {
        given(aiChatRoomService.findOrReserveRoom(USER_ID))
                .willReturn(room(ROOM_ID, AiChatRoomStatus.ANALYZING, LocalDateTime.now().minusMinutes(1)));

        AiConversationStartResult result = aiChatFacade.startOrResumeConversation(USER_ID);

        assertThat(result.isCreated()).isFalse();
        assertThat(result.conversation().status()).isEqualTo(AiChatRoomStatus.ANALYZING);
        verifyNoInteractions(aiChatClient);
        verify(aiChatRoomService, never()).expireAndReserveRoom(anyLong(), anyLong());
    }

    @Test
    void aiServerUnavailableWhileStarting_startOrResumeConversation_discardsReservedRoom() {
        given(aiChatRoomService.findOrReserveRoom(USER_ID)).willReturn(room(ROOM_ID, AiChatRoomStatus.PENDING, null));
        given(aiChatClient.startSession(USER_ID, ROOM_ID))
                .willThrow(new BusinessException(AiChatErrorCode.AICHAT_REQUEST_TIMEOUT));

        assertThatThrownBy(() -> aiChatFacade.startOrResumeConversation(USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(AiChatErrorCode.AICHAT_REQUEST_TIMEOUT);
        verify(aiChatRoomService).discardRoom(ROOM_ID);
        verify(aiChatRoomService, never()).activateRoom(anyLong(), any());
    }

    @Test
    void aiResponseWithoutGreeting_startOrResumeConversation_throwsInvalidResponseAndDiscardsRoom() {
        given(aiChatRoomService.findOrReserveRoom(USER_ID)).willReturn(room(ROOM_ID, AiChatRoomStatus.PENDING, null));
        given(aiChatClient.startSession(USER_ID, ROOM_ID))
                .willReturn(new AiServerStartSessionResponse(ROOM_ID, null, 20));

        assertThatThrownBy(() -> aiChatFacade.startOrResumeConversation(USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(AiChatErrorCode.AICHAT_INVALID_RESPONSE);
        verify(aiChatRoomService).discardRoom(ROOM_ID);
    }

    private static AiServerStartSessionResponse startSessionResponse(Long roomId) {
        return new AiServerStartSessionResponse(roomId, GREETING, 20);
    }

    private static AiChatRoom room(Long id, AiChatRoomStatus status, LocalDateTime purgeAt) {
        AiChatRoom room = AiChatRoom.reserve(USER_ID);
        // ID와 상태는 DB·이전 요청에서 결정되므로 단위 테스트에서만 직접 설정한다.
        ReflectionTestUtils.setField(room, "id", id);
        ReflectionTestUtils.setField(room, "status", status);
        ReflectionTestUtils.setField(room, "purgeAt", purgeAt);
        return room;
    }
}
