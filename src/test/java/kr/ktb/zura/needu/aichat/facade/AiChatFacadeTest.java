package kr.ktb.zura.needu.aichat.facade;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import kr.ktb.zura.needu.aichat.client.AiChatClient;
import kr.ktb.zura.needu.aichat.dto.request.SendMessageRequest;
import kr.ktb.zura.needu.aichat.dto.response.AiServerAnalysisKeywordsResponse;
import kr.ktb.zura.needu.aichat.dto.response.AiServerAnalysisResponse;
import kr.ktb.zura.needu.aichat.dto.response.AiMessageResponse;
import kr.ktb.zura.needu.aichat.dto.response.AiMessageSummaryResponse;
import kr.ktb.zura.needu.aichat.dto.response.AiServerSendMessageResponse;
import kr.ktb.zura.needu.aichat.dto.response.AiServerStartSessionResponse;
import kr.ktb.zura.needu.aichat.dto.response.AnalysisKeywordsResponse;
import kr.ktb.zura.needu.aichat.dto.response.AnalysisResultResponse;
import kr.ktb.zura.needu.aichat.entity.AiChatRoom;
import kr.ktb.zura.needu.aichat.entity.AiMessage;
import kr.ktb.zura.needu.aichat.exception.AiChatErrorCode;
import kr.ktb.zura.needu.aichat.service.AiChatRoomService;
import kr.ktb.zura.needu.aichat.service.AiConversationLock;
import kr.ktb.zura.needu.aichat.service.AiMessageService;
import kr.ktb.zura.needu.aichat.type.AiChatRoomStatus;
import kr.ktb.zura.needu.aichat.type.SenderType;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.TooManyRequestsException;
import kr.ktb.zura.needu.common.response.CursorPageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
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
    private static final Long USER_MESSAGE_ID = 201L;
    private static final Long AI_MESSAGE_ID = 202L;
    private static final String GREETING = "안녕하세요";
    private static final String USER_CONTENT = "요즘 러닝에 관심이 생겼어.";
    private static final String AI_CONTENT = "러닝을 좋아하시는군요.";
    private static final UUID CLIENT_MESSAGE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Duration MESSAGE_RETRY_AFTER = Duration.ofSeconds(10);

    @Mock
    private AiChatRoomService aiChatRoomService;

    @Mock
    private AiMessageService aiMessageService;

    @Mock
    private AiChatClient aiChatClient;

    private AiConversationLock aiConversationLock;
    private AiChatFacade aiChatFacade;

    @BeforeEach
    void setUp() {
        aiConversationLock = new AiConversationLock();
        aiChatFacade = new AiChatFacade(aiChatRoomService, aiMessageService, aiConversationLock,
                aiChatClient, MESSAGE_RETRY_AFTER);
    }

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

    @Test
    void newClientMessageId_sendMessage_savesMessagesAndExtendsSession() {
        givenActiveRoom();
        given(aiMessageService.findUserMessage(ROOM_ID, CLIENT_MESSAGE_ID)).willReturn(Optional.empty());
        given(aiMessageService.createUserMessage(ROOM_ID, CLIENT_MESSAGE_ID, USER_CONTENT))
                .willReturn(message(USER_MESSAGE_ID, null));
        given(aiChatClient.sendMessage(ROOM_ID, USER_CONTENT))
                .willReturn(new AiServerSendMessageResponse(AI_CONTENT));
        given(aiMessageService.createReply(ROOM_ID, USER_MESSAGE_ID, AI_CONTENT))
                .willReturn(message(AI_MESSAGE_ID, USER_MESSAGE_ID));

        AiMessageResponse response = aiChatFacade.sendMessage(USER_ID, ROOM_ID, sendMessageRequest());

        assertThat(response).isEqualTo(new AiMessageResponse(USER_MESSAGE_ID, AI_MESSAGE_ID, AI_CONTENT));
        verify(aiChatRoomService).extendSession(ROOM_ID);
    }

    @Test
    void duplicateClientMessageId_sendMessage_returnsSavedReplyWithoutCallingAiServer() {
        givenActiveRoom();
        given(aiMessageService.findUserMessage(ROOM_ID, CLIENT_MESSAGE_ID))
                .willReturn(Optional.of(message(USER_MESSAGE_ID, null)));
        given(aiMessageService.findReply(USER_MESSAGE_ID))
                .willReturn(Optional.of(message(AI_MESSAGE_ID, USER_MESSAGE_ID)));

        AiMessageResponse response = aiChatFacade.sendMessage(USER_ID, ROOM_ID, sendMessageRequest());

        assertThat(response).isEqualTo(new AiMessageResponse(USER_MESSAGE_ID, AI_MESSAGE_ID, AI_CONTENT));
        verifyNoInteractions(aiChatClient);
        verify(aiMessageService, never()).createUserMessage(anyLong(), any(), any());
    }

    @Test
    void replyNotSavedYet_sendMessage_throwsTooManyRequests() {
        givenActiveRoom();
        given(aiMessageService.findUserMessage(ROOM_ID, CLIENT_MESSAGE_ID))
                .willReturn(Optional.of(message(USER_MESSAGE_ID, null)));
        given(aiMessageService.findReply(USER_MESSAGE_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> aiChatFacade.sendMessage(USER_ID, ROOM_ID, sendMessageRequest()))
                .isInstanceOf(TooManyRequestsException.class);
        verifyNoInteractions(aiChatClient);
    }

    @Test
    void conversationAlreadyLocked_sendMessage_throwsTooManyRequests() {
        givenActiveRoom();
        aiConversationLock.tryLock(ROOM_ID);

        assertThatThrownBy(() -> aiChatFacade.sendMessage(USER_ID, ROOM_ID, sendMessageRequest()))
                .isInstanceOf(TooManyRequestsException.class);
        verifyNoInteractions(aiChatClient, aiMessageService);
    }

    @Test
    void duplicateInsertFromAnotherInstance_sendMessage_throwsTooManyRequests() {
        givenActiveRoom();
        given(aiMessageService.findUserMessage(ROOM_ID, CLIENT_MESSAGE_ID)).willReturn(Optional.empty());
        given(aiMessageService.createUserMessage(ROOM_ID, CLIENT_MESSAGE_ID, USER_CONTENT))
                .willThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> aiChatFacade.sendMessage(USER_ID, ROOM_ID, sendMessageRequest()))
                .isInstanceOf(TooManyRequestsException.class);
        verifyNoInteractions(aiChatClient);
    }

    @Test
    void aiSessionGone_sendMessage_expiresRoomAndThrowsNotFound() {
        givenActiveRoom();
        given(aiMessageService.findUserMessage(ROOM_ID, CLIENT_MESSAGE_ID)).willReturn(Optional.empty());
        given(aiMessageService.createUserMessage(ROOM_ID, CLIENT_MESSAGE_ID, USER_CONTENT))
                .willReturn(message(USER_MESSAGE_ID, null));
        given(aiChatClient.sendMessage(ROOM_ID, USER_CONTENT))
                .willThrow(new BusinessException(AiChatErrorCode.AICHAT_SESSION_NOT_FOUND));

        assertThatThrownBy(() -> aiChatFacade.sendMessage(USER_ID, ROOM_ID, sendMessageRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(AiChatErrorCode.AICHAT_SESSION_NOT_FOUND);
        verify(aiChatRoomService).expireRoom(ROOM_ID);
        verify(aiMessageService, never()).createReply(anyLong(), anyLong(), any());
    }

    @Test
    void aiTurnInProgress_sendMessage_throwsTooManyRequestsAndKeepsRoom() {
        givenActiveRoom();
        given(aiMessageService.findUserMessage(ROOM_ID, CLIENT_MESSAGE_ID)).willReturn(Optional.empty());
        given(aiMessageService.createUserMessage(ROOM_ID, CLIENT_MESSAGE_ID, USER_CONTENT))
                .willReturn(message(USER_MESSAGE_ID, null));
        given(aiChatClient.sendMessage(ROOM_ID, USER_CONTENT))
                .willThrow(new BusinessException(AiChatErrorCode.AICHAT_TURN_IN_PROGRESS));

        assertThatThrownBy(() -> aiChatFacade.sendMessage(USER_ID, ROOM_ID, sendMessageRequest()))
                .isInstanceOf(TooManyRequestsException.class);
        verify(aiChatRoomService, never()).expireRoom(anyLong());
        verify(aiMessageService, never()).createReply(anyLong(), anyLong(), any());
    }

    @Test
    void aiServerUnavailableWhileReplying_sendMessage_keepsRoomActive() {
        givenActiveRoom();
        given(aiMessageService.findUserMessage(ROOM_ID, CLIENT_MESSAGE_ID)).willReturn(Optional.empty());
        given(aiMessageService.createUserMessage(ROOM_ID, CLIENT_MESSAGE_ID, USER_CONTENT))
                .willReturn(message(USER_MESSAGE_ID, null));
        given(aiChatClient.sendMessage(ROOM_ID, USER_CONTENT))
                .willThrow(new BusinessException(AiChatErrorCode.AICHAT_REQUEST_TIMEOUT));

        assertThatThrownBy(() -> aiChatFacade.sendMessage(USER_ID, ROOM_ID, sendMessageRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(AiChatErrorCode.AICHAT_REQUEST_TIMEOUT);
        verify(aiChatRoomService, never()).expireRoom(anyLong());
    }

    @Test
    void aiResponseWithoutReply_sendMessage_throwsInvalidResponse() {
        givenActiveRoom();
        given(aiMessageService.findUserMessage(ROOM_ID, CLIENT_MESSAGE_ID)).willReturn(Optional.empty());
        given(aiMessageService.createUserMessage(ROOM_ID, CLIENT_MESSAGE_ID, USER_CONTENT))
                .willReturn(message(USER_MESSAGE_ID, null));
        given(aiChatClient.sendMessage(ROOM_ID, USER_CONTENT)).willReturn(new AiServerSendMessageResponse(" "));

        assertThatThrownBy(() -> aiChatFacade.sendMessage(USER_ID, ROOM_ID, sendMessageRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(AiChatErrorCode.AICHAT_INVALID_RESPONSE);
        verify(aiMessageService, never()).createReply(anyLong(), anyLong(), any());
    }

    @Test
    void inaccessibleConversation_sendMessage_doesNotHoldLock() {
        given(aiChatRoomService.findActiveRoom(USER_ID, ROOM_ID))
                .willThrow(new BusinessException(AiChatErrorCode.AICHAT_CONVERSATION_FORBIDDEN));

        assertThatThrownBy(() -> aiChatFacade.sendMessage(USER_ID, ROOM_ID, sendMessageRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(AiChatErrorCode.AICHAT_CONVERSATION_FORBIDDEN);
        assertThat(aiConversationLock.tryLock(ROOM_ID)).isTrue();
    }

    @Test
    void replyFailed_sendMessage_releasesLock() {
        givenActiveRoom();
        given(aiMessageService.findUserMessage(ROOM_ID, CLIENT_MESSAGE_ID)).willReturn(Optional.empty());
        given(aiMessageService.createUserMessage(ROOM_ID, CLIENT_MESSAGE_ID, USER_CONTENT))
                .willReturn(message(USER_MESSAGE_ID, null));
        given(aiChatClient.sendMessage(ROOM_ID, USER_CONTENT))
                .willThrow(new BusinessException(AiChatErrorCode.AICHAT_SERVER_UNAVAILABLE));

        assertThatThrownBy(() -> aiChatFacade.sendMessage(USER_ID, ROOM_ID, sendMessageRequest()))
                .isInstanceOf(BusinessException.class);

        assertThat(aiConversationLock.tryLock(ROOM_ID)).isTrue();
    }

    @Test
    void activeConversation_findAllMessages_returnsMessagePage() {
        givenActiveRoom();
        CursorPageResponse<AiMessageSummaryResponse> page = new CursorPageResponse<>(
                List.of(new AiMessageSummaryResponse(USER_MESSAGE_ID, SenderType.USER, USER_CONTENT,
                        LocalDateTime.now())),
                "cursor",
                true
        );
        given(aiMessageService.findAllMessages(ROOM_ID, null, 20)).willReturn(page);

        assertThat(aiChatFacade.findAllMessages(USER_ID, ROOM_ID, null, 20)).isEqualTo(page);
    }

    @Test
    void inaccessibleConversation_findAllMessages_doesNotReadMessages() {
        given(aiChatRoomService.findActiveRoom(USER_ID, ROOM_ID))
                .willThrow(new BusinessException(AiChatErrorCode.AICHAT_CONVERSATION_FORBIDDEN));

        assertThatThrownBy(() -> aiChatFacade.findAllMessages(USER_ID, ROOM_ID, null, 20))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(AiChatErrorCode.AICHAT_CONVERSATION_FORBIDDEN);
        verifyNoInteractions(aiMessageService);
    }

    @Test
    void activeConversation_createAnalysis_returnsMappedAnalysis() {
        givenActiveRoom();
        given(aiChatClient.createAnalysis(ROOM_ID)).willReturn(analysisResponse(true));

        AnalysisResultResponse response = aiChatFacade.createAnalysis(USER_ID, ROOM_ID);

        assertThat(response).isEqualTo(new AnalysisResultResponse(
                "캠핑과 핸드드립을 즐깁니다.",
                new AnalysisKeywordsResponse(
                        List.of("핸드드립", "가벼운 장비"),
                        List.of("캠핑", "티타늄 머그컵")
                ),
                true
        ));
    }

    @Test
    void analysisWithoutCorrectionAvailability_createAnalysis_throwsInvalidResponse() {
        givenActiveRoom();
        given(aiChatClient.createAnalysis(ROOM_ID)).willReturn(analysisResponse(null));

        assertThatThrownBy(() -> aiChatFacade.createAnalysis(USER_ID, ROOM_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(AiChatErrorCode.AICHAT_INVALID_RESPONSE);
    }

    private void givenActiveRoom() {
        given(aiChatRoomService.findActiveRoom(USER_ID, ROOM_ID))
                .willReturn(room(ROOM_ID, AiChatRoomStatus.ACTIVE, LocalDateTime.now().plusMinutes(29)));
    }

    private static SendMessageRequest sendMessageRequest() {
        return new SendMessageRequest(CLIENT_MESSAGE_ID, USER_CONTENT);
    }

    private static AiMessage message(Long id, Long replyToMessageId) {
        AiChatRoom room = room(ROOM_ID, AiChatRoomStatus.ACTIVE, null);
        AiMessage message = replyToMessageId == null
                ? AiMessage.createUserMessage(room, CLIENT_MESSAGE_ID, USER_CONTENT)
                : AiMessage.createReply(room, replyToMessageId, AI_CONTENT);
        // ID는 DB에서 결정되므로 단위 테스트에서만 직접 설정한다.
        ReflectionTestUtils.setField(message, "id", id);
        return message;
    }

    private static AiServerStartSessionResponse startSessionResponse(Long roomId) {
        return new AiServerStartSessionResponse(roomId, GREETING, 20);
    }

    private static AiServerAnalysisResponse analysisResponse(Boolean correctionAvailable) {
        return new AiServerAnalysisResponse(
                "캠핑과 핸드드립을 즐깁니다.",
                new AiServerAnalysisKeywordsResponse(
                        List.of("핸드드립", "가벼운 장비"),
                        List.of("캠핑", "티타늄 머그컵")
                ),
                correctionAvailable
        );
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
