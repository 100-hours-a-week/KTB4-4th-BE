package kr.ktb.zura.needu.aichat.facade;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import kr.ktb.zura.needu.aichat.client.AiChatClient;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerAnalysisProfileResponse;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerAnalysisKeywordResponse;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerCloseSessionResponse;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerRecommendationResult;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerRecommendedItem;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerRecommendationsResponse;
import kr.ktb.zura.needu.aichat.dto.request.AnalysisKeywordsRequest;
import kr.ktb.zura.needu.aichat.dto.request.PatchAnalyzeMessageRequest;
import kr.ktb.zura.needu.aichat.dto.request.SendMessageRequest;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerAnalysisKeywordsResponse;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerAnalysisResponse;
import kr.ktb.zura.needu.aichat.dto.response.AiMessageAcceptedResponse;
import kr.ktb.zura.needu.aichat.dto.response.AiMessageResponse;
import kr.ktb.zura.needu.aichat.dto.response.AiMessageSummaryResponse;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerSendMessageResponse;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerStartSessionResponse;
import kr.ktb.zura.needu.aichat.dto.response.AnalysisKeywordsResponse;
import kr.ktb.zura.needu.aichat.dto.response.AnalysisKeywordResponse;
import kr.ktb.zura.needu.aichat.dto.response.AnalysisResultResponse;
import kr.ktb.zura.needu.aichat.dto.response.ProductRecommendationStatusResponse;
import kr.ktb.zura.needu.aichat.dto.response.RestartRequiredResponse;
import kr.ktb.zura.needu.aichat.entity.AiChatRoom;
import kr.ktb.zura.needu.aichat.entity.AiMessage;
import kr.ktb.zura.needu.aichat.exception.AiChatErrorCode;
import kr.ktb.zura.needu.aichat.service.AiAnalysisConfirmService;
import kr.ktb.zura.needu.aichat.service.AiChatEventPublisher;
import kr.ktb.zura.needu.aichat.service.AiChatRoomService;
import kr.ktb.zura.needu.aichat.service.AiChatStreamService;
import kr.ktb.zura.needu.aichat.service.AiConversationLock;
import kr.ktb.zura.needu.aichat.service.AiGenerationStateService;
import kr.ktb.zura.needu.aichat.service.AiMessageService;
import kr.ktb.zura.needu.aichat.service.AiReplyTaskExecutor;
import kr.ktb.zura.needu.aichat.sse.AiChatAcceptedEvent;
import kr.ktb.zura.needu.aichat.sse.AiChatCompletedEvent;
import kr.ktb.zura.needu.aichat.sse.AiChatDeltaEvent;
import kr.ktb.zura.needu.aichat.sse.AiChatDoneEvent;
import kr.ktb.zura.needu.aichat.sse.AiChatErrorEvent;
import kr.ktb.zura.needu.aichat.sse.AiChatEventType;
import kr.ktb.zura.needu.aichat.type.AiChatRoomStatus;
import kr.ktb.zura.needu.aichat.type.SenderType;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.CommonErrorCode;
import kr.ktb.zura.needu.common.exception.TooManyRequestsException;
import kr.ktb.zura.needu.common.response.CursorPageResponse;
import kr.ktb.zura.needu.product.type.PlatformType;
import kr.ktb.zura.needu.user.dto.response.UserResponse;
import kr.ktb.zura.needu.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.stubbing.Answer;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AiChatFacadeTest {

    private static final Long USER_ID = 1L;
    private static final Long ROOM_ID = 101L;
    private static final Long NEW_ROOM_ID = 102L;
    private static final Long USER_MESSAGE_ID = 201L;
    private static final Long AI_MESSAGE_ID = 202L;
    private static final Long SHORT_COOLDOWN_EXTERNAL_ID = 123456789L;
    private static final String GREETING = "안녕하세요";
    private static final String USER_CONTENT = "요즘 러닝에 관심이 생겼어.";
    private static final String AI_CONTENT = "러닝을 좋아하시는군요.";
    private static final UUID CLIENT_MESSAGE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Duration MESSAGE_RETRY_AFTER = Duration.ofSeconds(10);
    private static final Duration SHORT_COMPLETION_COOLDOWN = Duration.ofMinutes(5);
    private static final OffsetDateTime EXPIRATION_AT = OffsetDateTime.parse("2026-09-23T05:48:00+00:00");
    private static final LocalDateTime PURGE_AT = LocalDateTime.of(2026, 9, 23, 5, 48);
    private static final AiConversationLock.Handle LOCK_HANDLE =
            new AiConversationLock.Handle(ROOM_ID, "lock-owner-token");

    @Mock
    private AiChatRoomService aiChatRoomService;

    @Mock
    private AiMessageService aiMessageService;

    @Mock
    private AiChatClient aiChatClient;

    @Mock
    private AiAnalysisConfirmService aiAnalysisConfirmService;

    @Mock
    private UserService userService;

    @Mock
    private AiConversationLock aiConversationLock;

    @Mock
    private AiGenerationStateService aiGenerationStateService;

    @Mock
    private AiChatStreamService aiChatStreamService;

    @Mock
    private AiChatEventPublisher aiChatEventPublisher;

    @Mock
    private AiReplyTaskExecutor aiReplyTaskExecutor;

    @Mock
    private AiReplyTaskExecutor.Slot replySlot;

    private AiChatFacade aiChatFacade;

    @BeforeEach
    void setUp() {
        lenient().when(aiConversationLock.tryLock(ROOM_ID)).thenReturn(Optional.of(LOCK_HANDLE));
        lenient().when(aiReplyTaskExecutor.tryReserve()).thenReturn(Optional.of(replySlot));
        // 백그라운드 답변 생성을 호출한 스레드에서 바로 실행해 결과를 확인한다
        lenient().doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).when(replySlot).execute(any());
        aiChatFacade = new AiChatFacade(aiChatRoomService, aiMessageService, aiConversationLock,
                aiGenerationStateService, aiChatClient, aiAnalysisConfirmService, aiChatStreamService,
                aiChatEventPublisher, aiReplyTaskExecutor, userService, MESSAGE_RETRY_AFTER,
                SHORT_COMPLETION_COOLDOWN, Set.of());
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
        assertThat(result.conversation().progress()).isZero();
    }

    @Test
    void shortCooldownUser_startOrResumeConversation_usesShortCooldown() {
        aiChatFacade = new AiChatFacade(aiChatRoomService, aiMessageService, aiConversationLock,
                aiGenerationStateService, aiChatClient, aiAnalysisConfirmService, aiChatStreamService,
                aiChatEventPublisher, aiReplyTaskExecutor, userService, MESSAGE_RETRY_AFTER,
                SHORT_COMPLETION_COOLDOWN, Set.of(SHORT_COOLDOWN_EXTERNAL_ID));
        given(userService.findAuthenticatedUser(USER_ID)).willReturn(new UserResponse(
                USER_ID, SHORT_COOLDOWN_EXTERNAL_ID, "테스트 사용자", null, true, null));
        given(aiChatRoomService.findOrReserveRoom(USER_ID, SHORT_COMPLETION_COOLDOWN))
                .willReturn(room(ROOM_ID, AiChatRoomStatus.PENDING, null));
        given(aiChatClient.startSession(USER_ID, ROOM_ID)).willReturn(startSessionResponse(ROOM_ID));
        given(aiChatRoomService.activateRoom(ROOM_ID, GREETING, PURGE_AT))
                .willReturn(room(ROOM_ID, AiChatRoomStatus.ACTIVE, PURGE_AT));

        AiConversationStartResult result = aiChatFacade.startOrResumeConversation(USER_ID);

        assertThat(result.isCreated()).isTrue();
        verify(aiChatRoomService).findOrReserveRoom(USER_ID, SHORT_COMPLETION_COOLDOWN);
    }

    @Test
    void activeRoomBeforePurgeAt_startOrResumeConversation_returnsResumedWithoutCallingAiServer() {
        given(aiChatRoomService.findOrReserveRoom(USER_ID))
                .willReturn(room(ROOM_ID, AiChatRoomStatus.ACTIVE,
                        LocalDateTime.now(ZoneOffset.UTC).plusMinutes(10)));
        given(aiMessageService.findLatestProgress(ROOM_ID)).willReturn(40);

        AiConversationStartResult result = aiChatFacade.startOrResumeConversation(USER_ID);

        assertThat(result.isCreated()).isFalse();
        assertThat(result.conversation().conversationId()).isEqualTo(ROOM_ID);
        assertThat(result.conversation().progress()).isEqualTo(40);
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
        assertThat(result.conversation().progress()).isZero();
    }

    @Test
    void analyzingRoomPastPurgeAt_startOrResumeConversation_expiresRoomAndCreatesNewConversation() {
        given(aiChatRoomService.findOrReserveRoom(USER_ID))
                .willReturn(room(ROOM_ID, AiChatRoomStatus.ANALYZING,
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
        verify(aiChatRoomService).expireAndReserveRoom(USER_ID, ROOM_ID);
    }

    @Test
    void analyzingRoomBeforePurgeAt_startOrResumeConversation_returnsResumedWithoutCallingAiServer() {
        given(aiChatRoomService.findOrReserveRoom(USER_ID))
                .willReturn(room(ROOM_ID, AiChatRoomStatus.ANALYZING,
                        LocalDateTime.now(ZoneOffset.UTC).plusMinutes(1)));
        given(aiMessageService.findLatestProgress(ROOM_ID)).willReturn(100);

        AiConversationStartResult result = aiChatFacade.startOrResumeConversation(USER_ID);

        assertThat(result.isCreated()).isFalse();
        assertThat(result.conversation().status()).isEqualTo(AiChatRoomStatus.ANALYZING);
        assertThat(result.conversation().progress()).isEqualTo(100);
        verifyNoInteractions(aiChatClient);
        verify(aiChatRoomService, never()).expireAndReserveRoom(anyLong(), anyLong());
    }

    @Test
    void recentlyCompletedRoom_startOrResumeConversation_returnsResumedEvenAfterPurgeAt() {
        AiChatRoom completedRoom = room(ROOM_ID, AiChatRoomStatus.COMPLETED,
                LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        OffsetDateTime nextConversationAvailableAt = OffsetDateTime.now(ZoneOffset.UTC).plusHours(23);
        given(aiChatRoomService.findOrReserveRoom(USER_ID)).willReturn(completedRoom);
        given(aiChatRoomService.findNextConversationAvailableAt(completedRoom))
                .willReturn(nextConversationAvailableAt);
        given(aiMessageService.findLatestProgress(ROOM_ID)).willReturn(100);

        AiConversationStartResult result = aiChatFacade.startOrResumeConversation(USER_ID);

        assertThat(result.isCreated()).isFalse();
        assertThat(result.conversation().status()).isEqualTo(AiChatRoomStatus.COMPLETED);
        assertThat(result.conversation().progress()).isEqualTo(100);
        assertThat(result.conversation().nextConversationAvailableAt())
                .isEqualTo(nextConversationAvailableAt);
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
    void newClientMessageId_sendMessage_returnsAcceptedAndSavesReplyInBackground() {
        givenNewMessage();
        given(streamMessageCall())
                .willAnswer(streamReply(sendMessageResponse(AI_CONTENT)));
        given(aiMessageService.createReplyAndUpdateRoom(
                ROOM_ID, USER_MESSAGE_ID, AI_CONTENT, 5, false, PURGE_AT))
                .willReturn(message(AI_MESSAGE_ID, USER_MESSAGE_ID));

        AiMessageSendResult result = aiChatFacade.sendMessage(USER_ID, ROOM_ID, sendMessageRequest());

        assertThat(result.isAccepted()).isTrue();
        assertThat(result.accepted()).isEqualTo(new AiMessageAcceptedResponse(USER_MESSAGE_ID, CLIENT_MESSAGE_ID));
        verify(aiMessageService).createReplyAndUpdateRoom(ROOM_ID, USER_MESSAGE_ID, AI_CONTENT, 5, false, PURGE_AT);
    }

    @Test
    void inputLocked_sendMessage_publishesLockedReplyWithoutRequestingAnalysis() {
        givenNewMessage();
        given(streamMessageCall())
                .willAnswer(streamReply(sendMessageResponse(AI_CONTENT, true)));
        given(aiMessageService.createReplyAndUpdateRoom(
                ROOM_ID, USER_MESSAGE_ID, AI_CONTENT, 5, true, PURGE_AT))
                .willReturn(message(AI_MESSAGE_ID, USER_MESSAGE_ID, true));

        aiChatFacade.sendMessage(USER_ID, ROOM_ID, sendMessageRequest());

        verify(aiChatEventPublisher).publish(ROOM_ID, AiChatEventType.COMPLETED,
                new AiChatCompletedEvent(USER_MESSAGE_ID, AI_MESSAGE_ID, AI_CONTENT, 5, true));
        verify(aiChatClient, never()).createAnalysis(anyLong());
    }

    @Test
    void duplicateClientMessageId_sendMessage_returnsSavedReplyWithoutLockOrAiCall() {
        given(aiMessageService.findUserMessage(ROOM_ID, CLIENT_MESSAGE_ID))
                .willReturn(Optional.of(message(USER_MESSAGE_ID, null)));
        given(aiMessageService.findReply(USER_MESSAGE_ID))
                .willReturn(Optional.of(message(AI_MESSAGE_ID, USER_MESSAGE_ID)));

        AiMessageSendResult result = aiChatFacade.sendMessage(USER_ID, ROOM_ID, sendMessageRequest());

        assertThat(result.isAccepted()).isFalse();
        assertThat(result.reply()).isEqualTo(
                new AiMessageResponse(USER_MESSAGE_ID, AI_MESSAGE_ID, AI_CONTENT, 5, false));
        verify(aiConversationLock, never()).tryLock(anyLong());
        verifyNoInteractions(aiChatClient, aiChatEventPublisher, aiReplyTaskExecutor);
        verify(aiMessageService, never()).createUserMessage(anyLong(), any(), any());
    }

    @Test
    void replyBeingGenerated_sendMessage_returnsSameAcceptedWithoutRegenerating() {
        given(aiMessageService.findUserMessage(ROOM_ID, CLIENT_MESSAGE_ID))
                .willReturn(Optional.of(message(USER_MESSAGE_ID, null)));
        given(aiMessageService.findReply(USER_MESSAGE_ID)).willReturn(Optional.empty());
        given(aiGenerationStateService.isGenerating(ROOM_ID, USER_MESSAGE_ID)).willReturn(true);

        AiMessageSendResult result = aiChatFacade.sendMessage(USER_ID, ROOM_ID, sendMessageRequest());

        assertThat(result.accepted()).isEqualTo(new AiMessageAcceptedResponse(USER_MESSAGE_ID, CLIENT_MESSAGE_ID));
        verify(aiConversationLock, never()).tryLock(anyLong());
        verify(aiGenerationStateService, never()).startGenerating(anyLong(), anyLong());
        verifyNoInteractions(aiChatClient, aiReplyTaskExecutor);
    }

    @Test
    void unansweredMessageNotGenerating_sendMessage_regeneratesReplyForSameMessage() {
        given(aiMessageService.findUserMessage(ROOM_ID, CLIENT_MESSAGE_ID))
                .willReturn(Optional.of(message(USER_MESSAGE_ID, null)));
        given(aiMessageService.findReply(USER_MESSAGE_ID)).willReturn(Optional.empty());
        given(aiGenerationStateService.isGenerating(ROOM_ID, USER_MESSAGE_ID)).willReturn(false);
        given(streamMessageCall())
                .willAnswer(streamReply(sendMessageResponse(AI_CONTENT)));
        given(aiMessageService.createReplyAndUpdateRoom(
                ROOM_ID, USER_MESSAGE_ID, AI_CONTENT, 5, false, PURGE_AT))
                .willReturn(message(AI_MESSAGE_ID, USER_MESSAGE_ID));

        AiMessageSendResult result = aiChatFacade.sendMessage(USER_ID, ROOM_ID, sendMessageRequest());

        assertThat(result.accepted()).isEqualTo(new AiMessageAcceptedResponse(USER_MESSAGE_ID, CLIENT_MESSAGE_ID));
        verify(aiMessageService, never()).createUserMessage(anyLong(), any(), any());
        verify(aiGenerationStateService).startGenerating(ROOM_ID, USER_MESSAGE_ID);
        verify(aiGenerationStateService).finishGenerating(ROOM_ID, USER_MESSAGE_ID);
    }

    @Test
    void replySavedWhileAcquiringLock_sendMessage_returnsSavedReplyAndReleasesLock() {
        given(aiMessageService.findUserMessage(ROOM_ID, CLIENT_MESSAGE_ID))
                .willReturn(Optional.of(message(USER_MESSAGE_ID, null)));
        given(aiMessageService.findReply(USER_MESSAGE_ID))
                .willReturn(Optional.empty(), Optional.of(message(AI_MESSAGE_ID, USER_MESSAGE_ID)));
        given(aiGenerationStateService.isGenerating(ROOM_ID, USER_MESSAGE_ID)).willReturn(false);

        AiMessageSendResult result = aiChatFacade.sendMessage(USER_ID, ROOM_ID, sendMessageRequest());

        assertThat(result.reply()).isEqualTo(
                new AiMessageResponse(USER_MESSAGE_ID, AI_MESSAGE_ID, AI_CONTENT, 5, false));
        verify(aiConversationLock).unlock(LOCK_HANDLE);
        verifyNoInteractions(aiChatClient, aiReplyTaskExecutor);
    }

    @Test
    void newClientMessageId_sendMessage_marksGeneratingBeforeStartingReplyTask() {
        givenNewMessage();
        given(streamMessageCall())
                .willAnswer(streamReply(sendMessageResponse(AI_CONTENT)));
        given(aiMessageService.createReplyAndUpdateRoom(
                ROOM_ID, USER_MESSAGE_ID, AI_CONTENT, 5, false, PURGE_AT))
                .willReturn(message(AI_MESSAGE_ID, USER_MESSAGE_ID));

        aiChatFacade.sendMessage(USER_ID, ROOM_ID, sendMessageRequest());

        var order = inOrder(aiReplyTaskExecutor, aiMessageService, aiGenerationStateService, replySlot,
                aiChatClient, aiConversationLock);
        order.verify(aiReplyTaskExecutor).tryReserve();
        order.verify(aiMessageService).createUserMessage(ROOM_ID, CLIENT_MESSAGE_ID, USER_CONTENT);
        order.verify(aiGenerationStateService).startGenerating(ROOM_ID, USER_MESSAGE_ID);
        order.verify(replySlot).execute(any());
        order.verify(aiChatClient).streamMessage(eq(USER_ID), eq(ROOM_ID), eq(USER_MESSAGE_ID), eq(USER_CONTENT), any());
        order.verify(aiMessageService).createReplyAndUpdateRoom(
                ROOM_ID, USER_MESSAGE_ID, AI_CONTENT, 5, false, PURGE_AT);
        order.verify(aiGenerationStateService).finishGenerating(ROOM_ID, USER_MESSAGE_ID);
        order.verify(aiConversationLock).unlock(LOCK_HANDLE);
    }

    @Test
    void newClientMessageId_sendMessage_publishesTurnEventsAndEndsTurnAfterUnlock() {
        givenNewMessage();
        given(streamMessageCall())
                .willAnswer(streamReply(sendMessageResponse(AI_CONTENT)));
        given(aiMessageService.createReplyAndUpdateRoom(
                ROOM_ID, USER_MESSAGE_ID, AI_CONTENT, 5, false, PURGE_AT))
                .willReturn(message(AI_MESSAGE_ID, USER_MESSAGE_ID));

        aiChatFacade.sendMessage(USER_ID, ROOM_ID, sendMessageRequest());

        var order = inOrder(aiChatEventPublisher, aiChatClient, aiMessageService, aiConversationLock);
        order.verify(aiChatEventPublisher).publish(ROOM_ID, AiChatEventType.ACCEPTED,
                new AiChatAcceptedEvent(USER_MESSAGE_ID, CLIENT_MESSAGE_ID));
        order.verify(aiChatClient).streamMessage(eq(USER_ID), eq(ROOM_ID), eq(USER_MESSAGE_ID), eq(USER_CONTENT), any());
        order.verify(aiChatEventPublisher).publish(ROOM_ID, AiChatEventType.DELTA,
                new AiChatDeltaEvent(USER_MESSAGE_ID, 0, AI_CONTENT));
        order.verify(aiMessageService).createReplyAndUpdateRoom(
                ROOM_ID, USER_MESSAGE_ID, AI_CONTENT, 5, false, PURGE_AT);
        order.verify(aiConversationLock).unlock(LOCK_HANDLE);
        order.verify(aiChatEventPublisher).publish(ROOM_ID, AiChatEventType.COMPLETED,
                new AiChatCompletedEvent(USER_MESSAGE_ID, AI_MESSAGE_ID, AI_CONTENT, 5, false));
        order.verify(aiChatEventPublisher).publish(ROOM_ID, AiChatEventType.DONE,
                new AiChatDoneEvent(USER_MESSAGE_ID));
    }

    @Test
    void replyFailed_sendMessage_returnsAcceptedAndPublishesErrorAndDoneAfterUnlock() {
        givenNewMessage();
        given(streamMessageCall())
                .willThrow(new BusinessException(AiChatErrorCode.AICHAT_SERVER_UNAVAILABLE));

        AiMessageSendResult result = aiChatFacade.sendMessage(USER_ID, ROOM_ID, sendMessageRequest());

        assertThat(result.isAccepted()).isTrue();
        var order = inOrder(aiChatEventPublisher, aiGenerationStateService, aiConversationLock);
        order.verify(aiChatEventPublisher).publish(ROOM_ID, AiChatEventType.ACCEPTED,
                new AiChatAcceptedEvent(USER_MESSAGE_ID, CLIENT_MESSAGE_ID));
        order.verify(aiGenerationStateService).finishGenerating(ROOM_ID, USER_MESSAGE_ID);
        order.verify(aiConversationLock).unlock(LOCK_HANDLE);
        order.verify(aiChatEventPublisher).publish(ROOM_ID, AiChatEventType.ERROR, new AiChatErrorEvent(
                USER_MESSAGE_ID, AiChatErrorCode.AICHAT_SERVER_UNAVAILABLE.getMessage(), true, false));
        order.verify(aiChatEventPublisher).publish(ROOM_ID, AiChatEventType.DONE,
                new AiChatDoneEvent(USER_MESSAGE_ID));
        verify(aiChatEventPublisher, never()).publish(eq(ROOM_ID), eq(AiChatEventType.DELTA), any());
        verify(aiChatRoomService, never()).expireRoom(anyLong());
    }

    @Test
    void aiSessionGone_sendMessage_expiresRoomAndPublishesRestartRequiredError() {
        givenNewMessage();
        given(streamMessageCall())
                .willThrow(new BusinessException(AiChatErrorCode.AICHAT_SESSION_NOT_FOUND));

        aiChatFacade.sendMessage(USER_ID, ROOM_ID, sendMessageRequest());

        verify(aiChatRoomService).expireRoom(ROOM_ID);
        verify(aiChatEventPublisher).publish(ROOM_ID, AiChatEventType.ERROR, new AiChatErrorEvent(
                USER_MESSAGE_ID, AiChatErrorCode.AICHAT_SESSION_NOT_FOUND.getMessage(), false, true));
        verify(aiMessageService, never()).createReplyAndUpdateRoom(
                anyLong(), anyLong(), any(), anyInt(), anyBoolean(), any());
    }

    @Test
    void aiTurnInProgress_sendMessage_publishesRetryableErrorAndKeepsRoom() {
        givenNewMessage();
        given(streamMessageCall())
                .willThrow(new BusinessException(AiChatErrorCode.AICHAT_TURN_IN_PROGRESS));

        aiChatFacade.sendMessage(USER_ID, ROOM_ID, sendMessageRequest());

        verify(aiChatEventPublisher).publish(ROOM_ID, AiChatEventType.ERROR, new AiChatErrorEvent(
                USER_MESSAGE_ID, AiChatErrorCode.AICHAT_TURN_IN_PROGRESS.getMessage(), true, false));
        verify(aiChatRoomService, never()).expireRoom(anyLong());
    }

    @Test
    void aiResponseWithoutReply_sendMessage_publishesErrorWithoutSavingReply() {
        givenNewMessage();
        given(streamMessageCall()).willAnswer(streamReply(sendMessageResponse(" ")));

        aiChatFacade.sendMessage(USER_ID, ROOM_ID, sendMessageRequest());

        verifyInvalidResponseError();
    }

    @Test
    void aiResponseWithInvalidProgress_sendMessage_publishesErrorWithoutSavingReply() {
        givenNewMessage();
        given(streamMessageCall())
                .willAnswer(streamReply(new AiServerSendMessageResponse(
                        AI_CONTENT, null, EXPIRATION_AT, 1, 20, false, false, 101)));

        aiChatFacade.sendMessage(USER_ID, ROOM_ID, sendMessageRequest());

        verifyInvalidResponseError();
    }

    @Test
    void aiResponseWithoutInputLocked_sendMessage_publishesErrorWithoutSavingReply() {
        givenNewMessage();
        given(streamMessageCall())
                .willAnswer(streamReply(new AiServerSendMessageResponse(
                        AI_CONTENT, null, EXPIRATION_AT, 1, 20, false, null, 5)));

        aiChatFacade.sendMessage(USER_ID, ROOM_ID, sendMessageRequest());

        verifyInvalidResponseError();
    }

    @Test
    void interruptedOnShutdown_sendMessage_clearsInterruptBeforeReleasingLock() {
        givenNewMessage();
        given(streamMessageCall()).willAnswer(invocation -> {
            Thread.currentThread().interrupt();
            throw new BusinessException(AiChatErrorCode.AICHAT_SERVER_UNAVAILABLE);
        });
        AtomicBoolean interruptedOnUnlock = new AtomicBoolean(true);
        willAnswer(invocation -> {
            interruptedOnUnlock.set(Thread.currentThread().isInterrupted());
            return null;
        }).given(aiConversationLock).unlock(LOCK_HANDLE);

        aiChatFacade.sendMessage(USER_ID, ROOM_ID, sendMessageRequest());

        assertThat(interruptedOnUnlock).isFalse();
        verify(aiChatEventPublisher).publish(eq(ROOM_ID), eq(AiChatEventType.ERROR), any());
    }

    @Test
    void eventPublishFails_sendMessage_stillSavesReply() {
        givenNewMessage();
        given(streamMessageCall())
                .willAnswer(streamReply(sendMessageResponse(AI_CONTENT)));
        given(aiMessageService.createReplyAndUpdateRoom(
                ROOM_ID, USER_MESSAGE_ID, AI_CONTENT, 5, false, PURGE_AT))
                .willReturn(message(AI_MESSAGE_ID, USER_MESSAGE_ID));
        willThrow(new IllegalStateException("redis down"))
                .given(aiChatEventPublisher).publish(eq(ROOM_ID), eq(AiChatEventType.ACCEPTED), any());

        AiMessageSendResult result = aiChatFacade.sendMessage(USER_ID, ROOM_ID, sendMessageRequest());

        assertThat(result.isAccepted()).isTrue();
        verify(aiMessageService).createReplyAndUpdateRoom(ROOM_ID, USER_MESSAGE_ID, AI_CONTENT, 5, false, PURGE_AT);
        verify(aiChatEventPublisher, times(1)).publish(anyLong(), any(), any());
    }

    @Test
    void conversationAlreadyLocked_sendMessage_throwsTooManyRequests() {
        given(aiMessageService.findUserMessage(ROOM_ID, CLIENT_MESSAGE_ID)).willReturn(Optional.empty());
        given(aiConversationLock.tryLock(ROOM_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> aiChatFacade.sendMessage(USER_ID, ROOM_ID, sendMessageRequest()))
                .isInstanceOf(TooManyRequestsException.class);
        verify(aiMessageService, never()).createUserMessage(anyLong(), any(), any());
        verifyNoInteractions(aiChatClient, aiReplyTaskExecutor);
    }

    @Test
    void replyCapacityExceeded_sendMessage_throwsServiceUnavailableWithoutSavingMessage() {
        given(aiMessageService.findUserMessage(ROOM_ID, CLIENT_MESSAGE_ID)).willReturn(Optional.empty());
        given(aiReplyTaskExecutor.tryReserve()).willReturn(Optional.empty());

        assertThatThrownBy(() -> aiChatFacade.sendMessage(USER_ID, ROOM_ID, sendMessageRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(CommonErrorCode.COMMON_SERVICE_UNAVAILABLE);
        verify(aiMessageService, never()).createUserMessage(anyLong(), any(), any());
        verify(aiConversationLock).unlock(LOCK_HANDLE);
        verifyNoInteractions(aiChatClient);
    }

    @Test
    void replyTaskRejected_sendMessage_clearsGeneratingStateAndReleasesLock() {
        givenNewMessage();
        willThrow(new BusinessException(CommonErrorCode.COMMON_SERVICE_UNAVAILABLE))
                .given(replySlot).execute(any());

        assertThatThrownBy(() -> aiChatFacade.sendMessage(USER_ID, ROOM_ID, sendMessageRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(CommonErrorCode.COMMON_SERVICE_UNAVAILABLE);
        verify(aiGenerationStateService).finishGenerating(ROOM_ID, USER_MESSAGE_ID);
        verify(replySlot).release();
        verify(aiConversationLock).unlock(LOCK_HANDLE);
        verifyNoInteractions(aiChatClient, aiChatEventPublisher);
    }

    @Test
    void duplicateInsertFromAnotherInstance_sendMessage_throwsTooManyRequestsWithoutEvents() {
        given(aiMessageService.findUserMessage(ROOM_ID, CLIENT_MESSAGE_ID)).willReturn(Optional.empty());
        given(aiMessageService.createUserMessage(ROOM_ID, CLIENT_MESSAGE_ID, USER_CONTENT))
                .willThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> aiChatFacade.sendMessage(USER_ID, ROOM_ID, sendMessageRequest()))
                .isInstanceOf(TooManyRequestsException.class);
        verify(replySlot).release();
        verify(aiConversationLock).unlock(LOCK_HANDLE);
        verifyNoInteractions(aiChatClient, aiChatEventPublisher);
    }

    @Test
    void inaccessibleConversation_sendMessage_doesNotHoldLock() {
        willThrow(new BusinessException(AiChatErrorCode.AICHAT_CONVERSATION_FORBIDDEN))
                .given(aiChatRoomService).validateMessageSendableRoom(USER_ID, ROOM_ID);

        assertThatThrownBy(() -> aiChatFacade.sendMessage(USER_ID, ROOM_ID, sendMessageRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(AiChatErrorCode.AICHAT_CONVERSATION_FORBIDDEN);
        verify(aiConversationLock, never()).tryLock(anyLong());
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
    void streamableConversation_connectStream_connectsFoundRoom() {
        AiChatRoom room = room(ROOM_ID, AiChatRoomStatus.ACTIVE, PURGE_AT);
        SseEmitter emitter = new SseEmitter();
        given(aiChatRoomService.findStreamableRoom(USER_ID, ROOM_ID)).willReturn(room);
        given(aiChatStreamService.connect(room)).willReturn(emitter);

        SseEmitter connected = aiChatFacade.connectStream(USER_ID, ROOM_ID);

        assertThat(connected).isSameAs(emitter);
        verify(userService).validateActiveUser(USER_ID);
    }

    @Test
    void inputLockedConversation_connectStream_throwsWithoutConnecting() {
        given(aiChatRoomService.findStreamableRoom(USER_ID, ROOM_ID))
                .willThrow(new BusinessException(AiChatErrorCode.AICHAT_INPUT_LOCKED));

        assertThatThrownBy(() -> aiChatFacade.connectStream(USER_ID, ROOM_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(AiChatErrorCode.AICHAT_INPUT_LOCKED);
        verifyNoInteractions(aiChatStreamService);
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
                .given(aiChatRoomService).validateReadableRoom(USER_ID, ROOM_ID);

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
    void aiSessionGone_createAnalysis_expiresRoomAndThrowsNotFound() {
        given(aiChatClient.createAnalysis(ROOM_ID))
                .willThrow(new BusinessException(AiChatErrorCode.AICHAT_SESSION_NOT_FOUND));

        assertThatThrownBy(() -> aiChatFacade.createAnalysis(USER_ID, ROOM_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(AiChatErrorCode.AICHAT_SESSION_NOT_FOUND);
        verify(aiChatRoomService).expireRoom(ROOM_ID);
    }

    @Test
    void profileTooSparse_createAnalysis_expiresRoomAndThrowsRestartRequired() {
        given(aiChatClient.createAnalysis(ROOM_ID))
                .willThrow(new BusinessException(AiChatErrorCode.AICHAT_PROFILE_TOO_SPARSE));

        assertThatThrownBy(() -> aiChatFacade.createAnalysis(USER_ID, ROOM_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode()).isEqualTo(AiChatErrorCode.AICHAT_PROFILE_TOO_SPARSE);
                    assertThat(businessException.getData()).isEqualTo(new RestartRequiredResponse(true));
                });
        verify(aiChatRoomService).expireRoom(ROOM_ID);
    }

    @Test
    void aiServerUnavailable_createAnalysis_keepsRoom() {
        given(aiChatClient.createAnalysis(ROOM_ID))
                .willThrow(new BusinessException(AiChatErrorCode.AICHAT_SERVER_UNAVAILABLE));

        assertThatThrownBy(() -> aiChatFacade.createAnalysis(USER_ID, ROOM_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(AiChatErrorCode.AICHAT_SERVER_UNAVAILABLE);
        verify(aiChatRoomService, never()).expireRoom(anyLong());
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
        verifyNoInteractions(aiMessageService, aiAnalysisConfirmService);
    }

    @Test
    void successfulRecommendationJob_confirmAnalysis_returnsCompleted() {
        AiServerCloseSessionResponse closeResponse = closeSessionResponse(List.of(), List.of("캠핑"));
        given(aiChatClient.confirmAnalysis(USER_ID, ROOM_ID)).willReturn(closeResponse);

        ProductRecommendationStatusResponse response = aiChatFacade.confirmAnalysis(USER_ID, ROOM_ID);

        assertThat(response.isRecommendationCompleted()).isTrue();
        var order = inOrder(aiChatRoomService, aiChatClient, aiAnalysisConfirmService);
        order.verify(aiChatRoomService).startAnalysis(ROOM_ID);
        order.verify(aiChatClient).confirmAnalysis(USER_ID, ROOM_ID);
        order.verify(aiAnalysisConfirmService).saveConfirmedAnalysis(USER_ID, ROOM_ID, closeResponse);
        verify(aiChatRoomService, never()).expireRoom(anyLong());
    }

    @Test
    void finalKeywordCountOverThree_confirmAnalysis_expiresRoomWithoutSavingResult() {
        given(aiChatClient.confirmAnalysis(USER_ID, ROOM_ID)).willReturn(closeSessionResponse(
                List.of("취향1", "취향2", "취향3", "취향4"),
                List.of("캠핑", "자전거타기", "여행")));

        assertThatThrownBy(() -> aiChatFacade.confirmAnalysis(USER_ID, ROOM_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(AiChatErrorCode.AICHAT_INVALID_RESPONSE);

        verifyNoInteractions(aiAnalysisConfirmService);
        verify(aiChatRoomService).expireRoom(ROOM_ID);
    }

    @Test
    void resultSaveFailsAfterSessionClosed_confirmAnalysis_expiresRoom() {
        AiServerCloseSessionResponse closeResponse = closeSessionResponse(List.of("실용적"), List.of("캠핑"));
        given(aiChatClient.confirmAnalysis(USER_ID, ROOM_ID)).willReturn(closeResponse);
        willThrow(new RuntimeException("save failed")).given(aiAnalysisConfirmService)
                .saveConfirmedAnalysis(USER_ID, ROOM_ID, closeResponse);

        assertThatThrownBy(() -> aiChatFacade.confirmAnalysis(USER_ID, ROOM_ID))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("save failed");

        verify(aiChatRoomService).expireRoom(ROOM_ID);
        verify(aiConversationLock).unlock(LOCK_HANDLE);
    }

    @Test
    void roomExpireAlsoFails_confirmAnalysis_throwsOriginalFailure() {
        AiServerCloseSessionResponse closeResponse = closeSessionResponse(List.of("실용적"), List.of("캠핑"));
        given(aiChatClient.confirmAnalysis(USER_ID, ROOM_ID)).willReturn(closeResponse);
        willThrow(new RuntimeException("save failed")).given(aiAnalysisConfirmService)
                .saveConfirmedAnalysis(USER_ID, ROOM_ID, closeResponse);
        willThrow(new RuntimeException("expire failed")).given(aiChatRoomService).expireRoom(ROOM_ID);

        assertThatThrownBy(() -> aiChatFacade.confirmAnalysis(USER_ID, ROOM_ID))
                .hasMessage("save failed")
                .satisfies(e -> assertThat(e.getSuppressed())
                        .singleElement().extracting(Throwable::getMessage).isEqualTo("expire failed"));
    }

    @Test
    void aiServerUnavailable_confirmAnalysis_keepsRoomForRetry() {
        given(aiChatClient.confirmAnalysis(USER_ID, ROOM_ID))
                .willThrow(new BusinessException(AiChatErrorCode.AICHAT_SERVER_UNAVAILABLE));

        assertThatThrownBy(() -> aiChatFacade.confirmAnalysis(USER_ID, ROOM_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(AiChatErrorCode.AICHAT_SERVER_UNAVAILABLE);

        verify(aiChatRoomService, never()).expireRoom(anyLong());
        verifyNoInteractions(aiAnalysisConfirmService);
    }

    @Test
    void confirmAlreadyInProgress_confirmAnalysis_throwsTooManyRequestsWithoutClosingSession() {
        given(aiConversationLock.tryLock(ROOM_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> aiChatFacade.confirmAnalysis(USER_ID, ROOM_ID))
                .isInstanceOf(TooManyRequestsException.class);

        verify(aiChatRoomService, never()).startAnalysis(anyLong());
        verifyNoInteractions(aiChatClient, aiAnalysisConfirmService);
    }

    private static AiServerCloseSessionResponse closeSessionResponse(List<String> tastes, List<String> interests) {
        AiServerRecommendationResult self = new AiServerRecommendationResult(List.of(
                new AiServerRecommendedItem(
                        PlatformType.COUPANG, "self-1", new java.math.BigDecimal("9.2"), "나를 위한 추천")));
        AiServerRecommendationResult gift = new AiServerRecommendationResult(List.of(
                new AiServerRecommendedItem(
                        PlatformType.COUPANG, "gift-1", new java.math.BigDecimal("8.0"), "선물 추천")));
        return new AiServerCloseSessionResponse(
                ROOM_ID, USER_ID, "캠핑을 즐깁니다.", keywords(tastes, interests),
                new AiServerRecommendationsResponse(self, gift));
    }

    // AI 답변 스트리밍 호출. 사용자 메시지 ID는 테스트마다 달라 어떤 값이든 받는다
    private AiServerSendMessageResponse streamMessageCall() {
        return aiChatClient.streamMessage(eq(USER_ID), eq(ROOM_ID), anyLong(), eq(USER_CONTENT), any());
    }

    // AI 서버가 답변 전체를 조각 하나로 보낸 뒤 completed를 보낸 것처럼 응답한다
    private static Answer<AiServerSendMessageResponse> streamReply(AiServerSendMessageResponse response) {
        return invocation -> {
            Consumer<String> onDelta = invocation.getArgument(4);
            if (response.reply() != null) {
                onDelta.accept(response.reply());
            }
            return response;
        };
    }

    private void givenNewMessage() {
        given(aiMessageService.findUserMessage(ROOM_ID, CLIENT_MESSAGE_ID)).willReturn(Optional.empty());
        given(aiMessageService.createUserMessage(ROOM_ID, CLIENT_MESSAGE_ID, USER_CONTENT))
                .willReturn(message(USER_MESSAGE_ID, null));
    }

    private void verifyInvalidResponseError() {
        verify(aiChatEventPublisher).publish(ROOM_ID, AiChatEventType.ERROR, new AiChatErrorEvent(
                USER_MESSAGE_ID, AiChatErrorCode.AICHAT_INVALID_RESPONSE.getMessage(), true, false));
        verify(aiMessageService, never()).createReplyAndUpdateRoom(
                anyLong(), anyLong(), any(), anyInt(), anyBoolean(), any());
        verify(aiConversationLock).unlock(LOCK_HANDLE);
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

    private static AiServerAnalysisKeywordsResponse keywords(List<String> tastes, List<String> interests) {
        return new AiServerAnalysisKeywordsResponse(
                tastes.stream().map(value -> new AiServerAnalysisKeywordResponse(value, 1.0)).toList(),
                interests.stream().map(value -> new AiServerAnalysisKeywordResponse(value, 1.0)).toList());
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
