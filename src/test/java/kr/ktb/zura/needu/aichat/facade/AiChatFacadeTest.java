package kr.ktb.zura.needu.aichat.facade;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import kr.ktb.zura.needu.aichat.client.AiChatClient;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerAnalysisProfileResponse;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerAnalysisKeywordResponse;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerCloseSessionResponse;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerCloseSessionKeywordsResponse;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerRecommendationResult;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerRecommendedItem;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerRecommendationsResponse;
import kr.ktb.zura.needu.aichat.dto.request.AnalysisKeywordsRequest;
import kr.ktb.zura.needu.aichat.dto.request.PatchAnalyzeMessageRequest;
import kr.ktb.zura.needu.aichat.dto.request.SendMessageRequest;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerAnalysisKeywordsResponse;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerAnalysisResponse;
import kr.ktb.zura.needu.aichat.dto.response.AiMessageResponse;
import kr.ktb.zura.needu.aichat.dto.response.AiMessageSummaryResponse;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerSendMessageResponse;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerStartSessionResponse;
import kr.ktb.zura.needu.aichat.dto.response.AnalysisKeywordsResponse;
import kr.ktb.zura.needu.aichat.dto.response.AnalysisKeywordResponse;
import kr.ktb.zura.needu.aichat.dto.response.AnalysisResultResponse;
import kr.ktb.zura.needu.aichat.dto.response.ProductRecommendationStatusResponse;
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
import kr.ktb.zura.needu.product.service.ProductRecommendationService;
import kr.ktb.zura.needu.product.type.PlatformType;
import kr.ktb.zura.needu.user.service.UserService;
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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
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
    private static final OffsetDateTime EXPIRATION_AT = OffsetDateTime.parse("2026-09-23T05:48:00+00:00");
    private static final LocalDateTime PURGE_AT = LocalDateTime.of(2026, 9, 23, 5, 48);

    @Mock
    private AiChatRoomService aiChatRoomService;

    @Mock
    private AiMessageService aiMessageService;

    @Mock
    private AiChatClient aiChatClient;

    @Mock
    private ProductRecommendationService productRecommendationService;

    @Mock
    private UserService userService;

    private AiConversationLock aiConversationLock;
    private AiChatFacade aiChatFacade;

    @BeforeEach
    void setUp() {
        aiConversationLock = new AiConversationLock();
        aiChatFacade = new AiChatFacade(aiChatRoomService, aiMessageService, aiConversationLock,
                aiChatClient, productRecommendationService, userService, MESSAGE_RETRY_AFTER);
    }

    @Test
    void noRoom_startOrResumeConversation_startsSessionAndReturnsCreated() {
        given(aiChatRoomService.findOrReserveRoom(USER_ID)).willReturn(room(ROOM_ID, AiChatRoomStatus.PENDING, null));
        given(aiChatClient.startSession(USER_ID, ROOM_ID)).willReturn(startSessionResponse(ROOM_ID));
        given(aiChatRoomService.activateRoom(ROOM_ID, GREETING, PURGE_AT))
                .willReturn(room(ROOM_ID, AiChatRoomStatus.ACTIVE,
                        LocalDateTime.now(ZoneOffset.UTC).plusMinutes(29)));

        AiConversationStartResult result = aiChatFacade.startOrResumeConversation(USER_ID);

        assertThat(result.isCreated()).isTrue();
        assertThat(result.conversation().conversationId()).isEqualTo(ROOM_ID);
        assertThat(result.conversation().status()).isEqualTo(AiChatRoomStatus.ACTIVE);
    }

    @Test
    void activeRoomBeforePurgeAt_startOrResumeConversation_returnsResumedWithoutCallingAiServer() {
        given(aiChatRoomService.findOrReserveRoom(USER_ID))
                .willReturn(room(ROOM_ID, AiChatRoomStatus.ACTIVE,
                        LocalDateTime.now(ZoneOffset.UTC).plusMinutes(10)));

        AiConversationStartResult result = aiChatFacade.startOrResumeConversation(USER_ID);

        assertThat(result.isCreated()).isFalse();
        assertThat(result.conversation().conversationId()).isEqualTo(ROOM_ID);
        verifyNoInteractions(aiChatClient);
    }

    @Test
    void activeRoomPastPurgeAt_startOrResumeConversation_expiresRoomAndCreatesNewConversation() {
        given(aiChatRoomService.findOrReserveRoom(USER_ID))
                .willReturn(room(ROOM_ID, AiChatRoomStatus.ACTIVE,
                        LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1)));
        given(aiChatRoomService.expireAndReserveRoom(USER_ID, ROOM_ID))
                .willReturn(room(NEW_ROOM_ID, AiChatRoomStatus.PENDING, null));
        given(aiChatClient.startSession(USER_ID, NEW_ROOM_ID)).willReturn(startSessionResponse(NEW_ROOM_ID));
        given(aiChatRoomService.activateRoom(NEW_ROOM_ID, GREETING, PURGE_AT))
                .willReturn(room(NEW_ROOM_ID, AiChatRoomStatus.ACTIVE,
                        LocalDateTime.now(ZoneOffset.UTC).plusMinutes(29)));

        AiConversationStartResult result = aiChatFacade.startOrResumeConversation(USER_ID);

        assertThat(result.isCreated()).isTrue();
        assertThat(result.conversation().conversationId()).isEqualTo(NEW_ROOM_ID);
    }

    @Test
    void analyzingRoomPastPurgeAt_startOrResumeConversation_returnsResumedWithoutCallingAiServer() {
        given(aiChatRoomService.findOrReserveRoom(USER_ID))
                .willReturn(room(ROOM_ID, AiChatRoomStatus.ANALYZING,
                        LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1)));

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
        verify(aiChatRoomService, never()).activateRoom(anyLong(), any(), any());
    }

    @Test
    void aiResponseWithoutGreeting_startOrResumeConversation_throwsInvalidResponseAndDiscardsRoom() {
        given(aiChatRoomService.findOrReserveRoom(USER_ID)).willReturn(room(ROOM_ID, AiChatRoomStatus.PENDING, null));
        given(aiChatClient.startSession(USER_ID, ROOM_ID))
                .willReturn(new AiServerStartSessionResponse(ROOM_ID, null, null, EXPIRATION_AT, 20));

        assertThatThrownBy(() -> aiChatFacade.startOrResumeConversation(USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(AiChatErrorCode.AICHAT_INVALID_RESPONSE);
        verify(aiChatRoomService).discardRoom(ROOM_ID);
    }

    @Test
    void differentConversationId_startOrResumeConversation_throwsInvalidResponseAndDiscardsRoom() {
        given(aiChatRoomService.findOrReserveRoom(USER_ID))
                .willReturn(room(ROOM_ID, AiChatRoomStatus.PENDING, null));
        given(aiChatClient.startSession(USER_ID, ROOM_ID))
                .willReturn(startSessionResponse(NEW_ROOM_ID));

        assertThatThrownBy(() -> aiChatFacade.startOrResumeConversation(USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(AiChatErrorCode.AICHAT_INVALID_RESPONSE);
        verify(aiChatRoomService).discardRoom(ROOM_ID);
        verify(aiChatRoomService, never()).activateRoom(anyLong(), any(), any());
    }

    @Test
    void newClientMessageId_sendMessage_savesMessagesAndExtendsSession() {
        given(aiMessageService.findUserMessage(ROOM_ID, CLIENT_MESSAGE_ID)).willReturn(Optional.empty());
        given(aiMessageService.createUserMessage(ROOM_ID, CLIENT_MESSAGE_ID, USER_CONTENT))
                .willReturn(message(USER_MESSAGE_ID, null));
        given(aiChatClient.sendMessage(USER_ID, ROOM_ID, USER_CONTENT))
                .willReturn(sendMessageResponse(AI_CONTENT));
        given(aiMessageService.createReplyAndUpdateRoom(
                ROOM_ID, USER_MESSAGE_ID, AI_CONTENT, 5, false, PURGE_AT))
                .willReturn(message(AI_MESSAGE_ID, USER_MESSAGE_ID));

        AiMessageResponse response = aiChatFacade.sendMessage(USER_ID, ROOM_ID, sendMessageRequest());

        assertThat(response).isEqualTo(
                new AiMessageResponse(USER_MESSAGE_ID, AI_MESSAGE_ID, AI_CONTENT, 5, false));
    }

    @Test
    void inputLocked_sendMessage_returnsReplyWithoutRequestingAnalysis() {
        given(aiMessageService.findUserMessage(ROOM_ID, CLIENT_MESSAGE_ID)).willReturn(Optional.empty());
        given(aiMessageService.createUserMessage(ROOM_ID, CLIENT_MESSAGE_ID, USER_CONTENT))
                .willReturn(message(USER_MESSAGE_ID, null));
        given(aiChatClient.sendMessage(USER_ID, ROOM_ID, USER_CONTENT))
                .willReturn(sendMessageResponse(AI_CONTENT, true));
        given(aiMessageService.createReplyAndUpdateRoom(
                ROOM_ID, USER_MESSAGE_ID, AI_CONTENT, 5, true, PURGE_AT))
                .willReturn(message(AI_MESSAGE_ID, USER_MESSAGE_ID, true));

        AiMessageResponse response = aiChatFacade.sendMessage(USER_ID, ROOM_ID, sendMessageRequest());

        assertThat(response.inputLocked()).isTrue();
        assertThat(response.progress()).isEqualTo(5);
        verify(aiChatClient).sendMessage(USER_ID, ROOM_ID, USER_CONTENT);
        verify(aiChatClient, never()).createAnalysis(anyLong());
    }

    @Test
    void duplicateClientMessageId_sendMessage_returnsSavedReplyWithoutCallingAiServer() {
        given(aiMessageService.findUserMessage(ROOM_ID, CLIENT_MESSAGE_ID))
                .willReturn(Optional.of(message(USER_MESSAGE_ID, null)));
        given(aiMessageService.findReply(USER_MESSAGE_ID))
                .willReturn(Optional.of(message(AI_MESSAGE_ID, USER_MESSAGE_ID)));

        AiMessageResponse response = aiChatFacade.sendMessage(USER_ID, ROOM_ID, sendMessageRequest());

        assertThat(response).isEqualTo(
                new AiMessageResponse(USER_MESSAGE_ID, AI_MESSAGE_ID, AI_CONTENT, 5, false));
        verifyNoInteractions(aiChatClient);
        verify(aiMessageService, never()).createUserMessage(anyLong(), any(), any());
    }

    @Test
    void replyNotSavedYet_sendMessage_throwsTooManyRequests() {
        given(aiMessageService.findUserMessage(ROOM_ID, CLIENT_MESSAGE_ID))
                .willReturn(Optional.of(message(USER_MESSAGE_ID, null)));
        given(aiMessageService.findReply(USER_MESSAGE_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> aiChatFacade.sendMessage(USER_ID, ROOM_ID, sendMessageRequest()))
                .isInstanceOf(TooManyRequestsException.class);
        verifyNoInteractions(aiChatClient);
    }

    @Test
    void conversationAlreadyLocked_sendMessage_throwsTooManyRequests() {
        aiConversationLock.tryLock(ROOM_ID);

        assertThatThrownBy(() -> aiChatFacade.sendMessage(USER_ID, ROOM_ID, sendMessageRequest()))
                .isInstanceOf(TooManyRequestsException.class);
        verifyNoInteractions(aiChatClient, aiMessageService);
    }

    @Test
    void duplicateInsertFromAnotherInstance_sendMessage_throwsTooManyRequests() {
        given(aiMessageService.findUserMessage(ROOM_ID, CLIENT_MESSAGE_ID)).willReturn(Optional.empty());
        given(aiMessageService.createUserMessage(ROOM_ID, CLIENT_MESSAGE_ID, USER_CONTENT))
                .willThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> aiChatFacade.sendMessage(USER_ID, ROOM_ID, sendMessageRequest()))
                .isInstanceOf(TooManyRequestsException.class);
        verifyNoInteractions(aiChatClient);
    }

    @Test
    void aiSessionGone_sendMessage_expiresRoomAndThrowsNotFound() {
        given(aiMessageService.findUserMessage(ROOM_ID, CLIENT_MESSAGE_ID)).willReturn(Optional.empty());
        given(aiMessageService.createUserMessage(ROOM_ID, CLIENT_MESSAGE_ID, USER_CONTENT))
                .willReturn(message(USER_MESSAGE_ID, null));
        given(aiChatClient.sendMessage(USER_ID, ROOM_ID, USER_CONTENT))
                .willThrow(new BusinessException(AiChatErrorCode.AICHAT_SESSION_NOT_FOUND));

        assertThatThrownBy(() -> aiChatFacade.sendMessage(USER_ID, ROOM_ID, sendMessageRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(AiChatErrorCode.AICHAT_SESSION_NOT_FOUND);
        verify(aiChatRoomService).expireRoom(ROOM_ID);
        verify(aiMessageService, never()).createReplyAndUpdateRoom(
                anyLong(), anyLong(), any(), anyInt(), anyBoolean(), any());
    }

    @Test
    void aiTurnInProgress_sendMessage_throwsTooManyRequestsAndKeepsRoom() {
        given(aiMessageService.findUserMessage(ROOM_ID, CLIENT_MESSAGE_ID)).willReturn(Optional.empty());
        given(aiMessageService.createUserMessage(ROOM_ID, CLIENT_MESSAGE_ID, USER_CONTENT))
                .willReturn(message(USER_MESSAGE_ID, null));
        given(aiChatClient.sendMessage(USER_ID, ROOM_ID, USER_CONTENT))
                .willThrow(new BusinessException(AiChatErrorCode.AICHAT_TURN_IN_PROGRESS));

        assertThatThrownBy(() -> aiChatFacade.sendMessage(USER_ID, ROOM_ID, sendMessageRequest()))
                .isInstanceOf(TooManyRequestsException.class);
        verify(aiChatRoomService, never()).expireRoom(anyLong());
        verify(aiMessageService, never()).createReplyAndUpdateRoom(
                anyLong(), anyLong(), any(), anyInt(), anyBoolean(), any());
    }

    @Test
    void aiServerUnavailableWhileReplying_sendMessage_keepsRoomActive() {
        given(aiMessageService.findUserMessage(ROOM_ID, CLIENT_MESSAGE_ID)).willReturn(Optional.empty());
        given(aiMessageService.createUserMessage(ROOM_ID, CLIENT_MESSAGE_ID, USER_CONTENT))
                .willReturn(message(USER_MESSAGE_ID, null));
        given(aiChatClient.sendMessage(USER_ID, ROOM_ID, USER_CONTENT))
                .willThrow(new BusinessException(AiChatErrorCode.AICHAT_REQUEST_TIMEOUT));

        assertThatThrownBy(() -> aiChatFacade.sendMessage(USER_ID, ROOM_ID, sendMessageRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(AiChatErrorCode.AICHAT_REQUEST_TIMEOUT);
        verify(aiChatRoomService, never()).expireRoom(anyLong());
    }

    @Test
    void aiResponseWithoutReply_sendMessage_throwsInvalidResponse() {
        given(aiMessageService.findUserMessage(ROOM_ID, CLIENT_MESSAGE_ID)).willReturn(Optional.empty());
        given(aiMessageService.createUserMessage(ROOM_ID, CLIENT_MESSAGE_ID, USER_CONTENT))
                .willReturn(message(USER_MESSAGE_ID, null));
        given(aiChatClient.sendMessage(USER_ID, ROOM_ID, USER_CONTENT)).willReturn(sendMessageResponse(" "));

        assertThatThrownBy(() -> aiChatFacade.sendMessage(USER_ID, ROOM_ID, sendMessageRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(AiChatErrorCode.AICHAT_INVALID_RESPONSE);
        verify(aiMessageService, never()).createReplyAndUpdateRoom(
                anyLong(), anyLong(), any(), anyInt(), anyBoolean(), any());
    }

    @Test
    void aiResponseWithInvalidProgress_sendMessage_throwsInvalidResponse() {
        given(aiMessageService.findUserMessage(ROOM_ID, CLIENT_MESSAGE_ID)).willReturn(Optional.empty());
        given(aiMessageService.createUserMessage(ROOM_ID, CLIENT_MESSAGE_ID, USER_CONTENT))
                .willReturn(message(USER_MESSAGE_ID, null));
        given(aiChatClient.sendMessage(USER_ID, ROOM_ID, USER_CONTENT))
                .willReturn(new AiServerSendMessageResponse(
                        AI_CONTENT, null, EXPIRATION_AT, 1, 20, false, false, 101));

        assertThatThrownBy(() -> aiChatFacade.sendMessage(USER_ID, ROOM_ID, sendMessageRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(AiChatErrorCode.AICHAT_INVALID_RESPONSE);
        verify(aiMessageService, never()).createReplyAndUpdateRoom(
                anyLong(), anyLong(), any(), anyInt(), anyBoolean(), any());
    }

    @Test
    void aiResponseWithoutInputLocked_sendMessage_throwsInvalidResponse() {
        given(aiMessageService.findUserMessage(ROOM_ID, CLIENT_MESSAGE_ID)).willReturn(Optional.empty());
        given(aiMessageService.createUserMessage(ROOM_ID, CLIENT_MESSAGE_ID, USER_CONTENT))
                .willReturn(message(USER_MESSAGE_ID, null));
        given(aiChatClient.sendMessage(USER_ID, ROOM_ID, USER_CONTENT))
                .willReturn(new AiServerSendMessageResponse(
                        AI_CONTENT, null, EXPIRATION_AT, 1, 20, false, null, 5));

        assertThatThrownBy(() -> aiChatFacade.sendMessage(USER_ID, ROOM_ID, sendMessageRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(AiChatErrorCode.AICHAT_INVALID_RESPONSE);
        verify(aiMessageService, never()).createReplyAndUpdateRoom(
                anyLong(), anyLong(), any(), anyInt(), anyBoolean(), any());
    }

    @Test
    void inaccessibleConversation_sendMessage_doesNotHoldLock() {
        willThrow(new BusinessException(AiChatErrorCode.AICHAT_CONVERSATION_FORBIDDEN))
                .given(aiChatRoomService).validateMessageSendableRoom(USER_ID, ROOM_ID);

        assertThatThrownBy(() -> aiChatFacade.sendMessage(USER_ID, ROOM_ID, sendMessageRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(AiChatErrorCode.AICHAT_CONVERSATION_FORBIDDEN);
        assertThat(aiConversationLock.tryLock(ROOM_ID)).isTrue();
    }

    @Test
    void inputLockedConversation_sendMessage_throwsConflictWithoutCallingAiServer() {
        willThrow(new BusinessException(AiChatErrorCode.AICHAT_INPUT_LOCKED))
                .given(aiChatRoomService).validateMessageSendableRoom(USER_ID, ROOM_ID);

        assertThatThrownBy(() -> aiChatFacade.sendMessage(USER_ID, ROOM_ID, sendMessageRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(AiChatErrorCode.AICHAT_INPUT_LOCKED);
        verifyNoInteractions(aiChatClient);
    }

    @Test
    void replyFailed_sendMessage_releasesLock() {
        given(aiMessageService.findUserMessage(ROOM_ID, CLIENT_MESSAGE_ID)).willReturn(Optional.empty());
        given(aiMessageService.createUserMessage(ROOM_ID, CLIENT_MESSAGE_ID, USER_CONTENT))
                .willReturn(message(USER_MESSAGE_ID, null));
        given(aiChatClient.sendMessage(USER_ID, ROOM_ID, USER_CONTENT))
                .willThrow(new BusinessException(AiChatErrorCode.AICHAT_SERVER_UNAVAILABLE));

        assertThatThrownBy(() -> aiChatFacade.sendMessage(USER_ID, ROOM_ID, sendMessageRequest()))
                .isInstanceOf(BusinessException.class);

        assertThat(aiConversationLock.tryLock(ROOM_ID)).isTrue();
    }

    @Test
    void activeConversation_findAllMessages_returnsMessagePage() {
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
        willThrow(new BusinessException(AiChatErrorCode.AICHAT_CONVERSATION_FORBIDDEN))
                .given(aiChatRoomService).validateActiveRoom(USER_ID, ROOM_ID);

        assertThatThrownBy(() -> aiChatFacade.findAllMessages(USER_ID, ROOM_ID, null, 20))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(AiChatErrorCode.AICHAT_CONVERSATION_FORBIDDEN);
        verifyNoInteractions(aiMessageService);
    }

    @Test
    void analyzableConversation_createAnalysis_returnsMappedAnalysis() {
        given(aiChatClient.createAnalysis(ROOM_ID)).willReturn(analysisResponse(true));

        AnalysisResultResponse response = aiChatFacade.createAnalysis(USER_ID, ROOM_ID);

        assertThat(response).isEqualTo(new AnalysisResultResponse(
                "캠핑과 핸드드립을 즐깁니다.",
                new AnalysisKeywordsResponse(
                        List.of(
                                new AnalysisKeywordResponse("핸드드립", 0.92),
                                new AnalysisKeywordResponse("가벼운 장비", 0.81)),
                        List.of(
                                new AnalysisKeywordResponse("캠핑", 0.95),
                                new AnalysisKeywordResponse("티타늄 머그컵", 0.76))
                ),
                true
        ));
        verify(aiChatRoomService).validateAnalyzableRoom(USER_ID, ROOM_ID);
    }

    @Test
    void analysisNotReady_createAnalysis_doesNotCallAiServer() {
        willThrow(new BusinessException(AiChatErrorCode.AICHAT_ANALYSIS_NOT_READY))
                .given(aiChatRoomService).validateAnalyzableRoom(USER_ID, ROOM_ID);

        assertThatThrownBy(() -> aiChatFacade.createAnalysis(USER_ID, ROOM_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(AiChatErrorCode.AICHAT_ANALYSIS_NOT_READY);
        verifyNoInteractions(aiChatClient);
    }

    @Test
    void analysisWithoutCorrectionAvailability_createAnalysis_throwsInvalidResponse() {
        given(aiChatClient.createAnalysis(ROOM_ID)).willReturn(analysisResponse(null));

        assertThatThrownBy(() -> aiChatFacade.createAnalysis(USER_ID, ROOM_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(AiChatErrorCode.AICHAT_INVALID_RESPONSE);
    }

    @Test
    void analysisWithBlankSummary_createAnalysis_throwsInvalidResponse() {
        AiServerAnalysisResponse response = analysisResponse(true);
        given(aiChatClient.createAnalysis(ROOM_ID)).willReturn(new AiServerAnalysisResponse(
                new AiServerAnalysisProfileResponse(
                        USER_ID, " ", response.profile().keywords(), true)));

        assertThatThrownBy(() -> aiChatFacade.createAnalysis(USER_ID, ROOM_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(AiChatErrorCode.AICHAT_INVALID_RESPONSE);
    }

    @Test
    void analysisWithInvalidKeywordScore_createAnalysis_throwsInvalidResponse() {
        given(aiChatClient.createAnalysis(ROOM_ID)).willReturn(new AiServerAnalysisResponse(
                new AiServerAnalysisProfileResponse(
                        USER_ID,
                        "캠핑을 즐깁니다.",
                        new AiServerAnalysisKeywordsResponse(
                                List.of(new AiServerAnalysisKeywordResponse("캠핑", 1.1)),
                                List.of()),
                        true)));

        assertThatThrownBy(() -> aiChatFacade.createAnalysis(USER_ID, ROOM_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(AiChatErrorCode.AICHAT_INVALID_RESPONSE);
    }

    @Test
    void validRequest_patchAnalysis_returnsAiResultWithoutSaving() {
        PatchAnalyzeMessageRequest request = new PatchAnalyzeMessageRequest(
                "주말마다 자연에서 쉬는 것을 좋아합니다.",
                new AnalysisKeywordsRequest(List.of("실용적"), List.of("캠핑")));
        given(aiChatClient.patchAnalyze(eq(ROOM_ID), any())).willReturn(analysisResponse(false));

        AnalysisResultResponse response = aiChatFacade.patchAnalyze(USER_ID, ROOM_ID, request);

        assertThat(response.summary()).isEqualTo("캠핑과 핸드드립을 즐깁니다.");
        verify(aiChatRoomService).validateActiveRoom(USER_ID, ROOM_ID);
        verify(aiChatClient).patchAnalyze(eq(ROOM_ID), any());
        verifyNoInteractions(aiMessageService, productRecommendationService, userService);
    }

    @Test
    void successfulRecommendationJob_confirmAnalysis_returnsCompleted() {
        AiServerRecommendationResult self = new AiServerRecommendationResult(List.of(
                new AiServerRecommendedItem(
                        PlatformType.COUPANG, "self-1", new java.math.BigDecimal("9.2"), "나를 위한 추천")));
        AiServerRecommendationResult gift = new AiServerRecommendationResult(List.of(
                new AiServerRecommendedItem(
                        PlatformType.COUPANG, "gift-1", new java.math.BigDecimal("8.0"), "선물 추천")));
        List<String> tastes = List.of();
        List<String> interests = List.of("캠핑");
        given(aiChatClient.confirmAnalysis(USER_ID, ROOM_ID))
                .willReturn(new AiServerCloseSessionResponse(
                        ROOM_ID,
                        USER_ID,
                        "캠핑을 즐깁니다.",
                        new AiServerCloseSessionKeywordsResponse(tastes, interests),
                        new AiServerRecommendationsResponse(self, gift)
                ));

        ProductRecommendationStatusResponse response = aiChatFacade.confirmAnalysis(USER_ID, ROOM_ID);

        assertThat(response.isRecommendationCompleted()).isTrue();
        var order = inOrder(aiChatRoomService, aiChatClient);
        order.verify(aiChatRoomService).startAnalysis(ROOM_ID);
        order.verify(aiChatClient).confirmAnalysis(USER_ID, ROOM_ID);
        order.verify(aiChatRoomService).completeRoom(ROOM_ID);
        verify(productRecommendationService).saveRecommendations(
                USER_ID, self, gift, tastes);
        verify(userService).completeTasteAnalysis(
                USER_ID, "캠핑을 즐깁니다.", tastes, interests);
    }

    @Test
    void finalKeywordCountOverThree_confirmAnalysis_doesNotSaveResult() {
        AiServerRecommendationResult recommendations = new AiServerRecommendationResult(List.of());
        given(aiChatClient.confirmAnalysis(USER_ID, ROOM_ID))
                .willReturn(new AiServerCloseSessionResponse(
                        ROOM_ID,
                        USER_ID,
                        "캠핑을 즐깁니다.",
                        new AiServerCloseSessionKeywordsResponse(
                                List.of("취향1", "취향2", "취향3", "취향4"),
                                List.of("캠핑", "자전거타기", "여행")),
                        new AiServerRecommendationsResponse(recommendations, recommendations)
                ));

        assertThatThrownBy(() -> aiChatFacade.confirmAnalysis(USER_ID, ROOM_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(AiChatErrorCode.AICHAT_INVALID_RESPONSE);

        verifyNoInteractions(productRecommendationService, userService);
        verify(aiChatRoomService, never()).completeRoom(ROOM_ID);
    }

    @Test
    void recommendationSaveFails_confirmAnalysis_doesNotCompleteRoom() {
        AiServerRecommendationResult self = new AiServerRecommendationResult(List.of());
        AiServerRecommendationResult gift = new AiServerRecommendationResult(List.of());
        List<String> tastes = List.of("실용적", "가벼운 장비", "핸드드립");
        List<String> interests = List.of("캠핑", "자전거타기", "여행");
        given(aiChatClient.confirmAnalysis(USER_ID, ROOM_ID))
                .willReturn(new AiServerCloseSessionResponse(
                        ROOM_ID,
                        USER_ID,
                        "캠핑을 즐깁니다.",
                        new AiServerCloseSessionKeywordsResponse(tastes, interests),
                        new AiServerRecommendationsResponse(self, gift)
                ));
        willThrow(new RuntimeException("save failed")).given(productRecommendationService)
                .saveRecommendations(USER_ID, self, gift, tastes);

        assertThatThrownBy(() -> aiChatFacade.confirmAnalysis(USER_ID, ROOM_ID))
                .isInstanceOf(RuntimeException.class);

        verify(userService, never()).completeTasteAnalysis(anyLong(), any(), any(), any());
        verify(aiChatRoomService, never()).completeRoom(ROOM_ID);
    }

    private static SendMessageRequest sendMessageRequest() {
        return new SendMessageRequest(CLIENT_MESSAGE_ID, USER_CONTENT);
    }

    private static AiMessage message(Long id, Long replyToMessageId) {
        return message(id, replyToMessageId, false);
    }

    private static AiMessage message(Long id, Long replyToMessageId, boolean inputLocked) {
        AiChatRoom room = room(ROOM_ID, AiChatRoomStatus.ACTIVE, null);
        AiMessage message = replyToMessageId == null
                ? AiMessage.createUserMessage(room, CLIENT_MESSAGE_ID, USER_CONTENT)
                : AiMessage.createReply(room, replyToMessageId, AI_CONTENT, 5, inputLocked);
        // ID는 DB에서 결정되므로 단위 테스트에서만 직접 설정한다.
        ReflectionTestUtils.setField(message, "id", id);
        return message;
    }

    private static AiServerStartSessionResponse startSessionResponse(Long roomId) {
        return new AiServerStartSessionResponse(roomId, GREETING, null, EXPIRATION_AT, 20);
    }

    private static AiServerAnalysisResponse analysisResponse(Boolean correctionAvailable) {
        return new AiServerAnalysisResponse(new AiServerAnalysisProfileResponse(
                USER_ID,
                "캠핑과 핸드드립을 즐깁니다.",
                new AiServerAnalysisKeywordsResponse(
                        List.of(
                                new AiServerAnalysisKeywordResponse("핸드드립", 0.92),
                                new AiServerAnalysisKeywordResponse("가벼운 장비", 0.81)),
                        List.of(
                                new AiServerAnalysisKeywordResponse("캠핑", 0.95),
                                new AiServerAnalysisKeywordResponse("티타늄 머그컵", 0.76))),
                correctionAvailable));
    }

    private static AiServerSendMessageResponse sendMessageResponse(String reply) {
        return sendMessageResponse(reply, false);
    }

    private static AiServerSendMessageResponse sendMessageResponse(String reply, boolean inputLocked) {
        return new AiServerSendMessageResponse(
                reply, null, EXPIRATION_AT, 1, 20, false, inputLocked, 5);
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
