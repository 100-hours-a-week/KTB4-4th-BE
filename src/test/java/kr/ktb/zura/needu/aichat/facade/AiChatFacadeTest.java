package kr.ktb.zura.needu.aichat.facade;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import kr.ktb.zura.needu.aichat.client.AiChatClient;
import kr.ktb.zura.needu.aichat.client.dto.request.AiServerFeedbackItemRequest;
import kr.ktb.zura.needu.aichat.client.dto.request.AiServerFeedbackRequest;
import kr.ktb.zura.needu.aichat.client.dto.request.AiServerUserProfileRequest;
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
import kr.ktb.zura.needu.aichat.config.AiReplyQueueProperties;
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
import kr.ktb.zura.needu.aichat.service.AiReplyCommitResult;
import kr.ktb.zura.needu.aichat.service.AiReplyJobDispatch;
import kr.ktb.zura.needu.aichat.service.AiReplyJobService;
import kr.ktb.zura.needu.aichat.service.AiReplyJobTakeResult;
import kr.ktb.zura.needu.aichat.service.AiReplyQueueService;
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
import kr.ktb.zura.needu.product.dto.response.ProductActivityResponse;
import kr.ktb.zura.needu.product.service.ProductActivityService;
import kr.ktb.zura.needu.product.type.PlatformType;
import kr.ktb.zura.needu.product.type.ProductActivityType;
import kr.ktb.zura.needu.product.type.ProductContext;
import kr.ktb.zura.needu.user.dto.response.UserAiProfileResponse;
import kr.ktb.zura.needu.user.dto.response.UserResponse;
import kr.ktb.zura.needu.user.exception.UserErrorCode;
import kr.ktb.zura.needu.user.service.ConsentService;
import kr.ktb.zura.needu.user.service.UserService;
import kr.ktb.zura.needu.user.type.Gender;
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
import static org.mockito.BDDMockito.willDoNothing;
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
    private static final String OTHER_AI_CONTENT = "러닝을 즐기시는군요.";
    private static final UUID CLIENT_MESSAGE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Duration MESSAGE_RETRY_AFTER = Duration.ofSeconds(10);
    private static final Duration SHORT_COMPLETION_COOLDOWN = Duration.ofMinutes(5);
    private static final OffsetDateTime EXPIRATION_AT = OffsetDateTime.parse("2026-09-23T05:48:00+00:00");
    private static final LocalDateTime PURGE_AT = LocalDateTime.of(2026, 9, 23, 5, 48);
    private static final UserAiProfileResponse USER_AI_PROFILE = new UserAiProfileResponse(
            "니듀", LocalDate.of(2000, 1, 1), Gender.FEMALE, List.of("COFFEE_TEA"), List.of("PEANUT"), List.of());
    private static final AiServerUserProfileRequest USER_PROFILE_REQUEST =
            AiServerUserProfileRequest.from(USER_AI_PROFILE);
    private static final Map<String, Object> MEMORY = Map.of("schemaVersion", "1.0", "userId", USER_ID);
    private static final String LOCK_TOKEN = "lock-owner-token";
    private static final AiConversationLock.Handle LOCK_HANDLE = new AiConversationLock.Handle(ROOM_ID, LOCK_TOKEN);
    private static final Long JOB_ID = 301L;
    private static final String DISPATCH_TOKEN = "dispatch-token";
    private static final int ATTEMPT = 1;
    private static final AiReplyQueueProperties QUEUE_DISABLED =
            new AiReplyQueueProperties(false, 96, Duration.ofSeconds(30), 3,
                    Duration.ofSeconds(5), Duration.ofSeconds(30), Duration.ofSeconds(5), Duration.ofMillis(200));
    private static final AiReplyQueueProperties QUEUE_ENABLED =
            new AiReplyQueueProperties(true, 96, Duration.ofSeconds(30), 3,
                    Duration.ofSeconds(5), Duration.ofSeconds(30), Duration.ofSeconds(5), Duration.ofMillis(200));

    @Mock
    private AiChatRoomService aiChatRoomService;

    @Mock
    private AiMessageService aiMessageService;

    @Mock
    private AiReplyJobService aiReplyJobService;

    @Mock
    private AiChatClient aiChatClient;

    @Mock
    private AiAnalysisConfirmService aiAnalysisConfirmService;

    @Mock
    private UserService userService;

    @Mock
    private ConsentService consentService;

    @Mock
    private ProductActivityService productActivityService;

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

    @Mock
    private AiReplyQueueService aiReplyQueueService;

    private AiChatFacade aiChatFacade;

    @BeforeEach
    void setUp() {
        lenient().when(aiConversationLock.tryLock(ROOM_ID)).thenReturn(Optional.of(LOCK_HANDLE));
        lenient().when(consentService.hasAgreedToAiConversation(USER_ID)).thenReturn(true);
        lenient().when(aiReplyTaskExecutor.tryReserve()).thenReturn(Optional.of(replySlot));
        lenient().when(aiConversationLock.ensure(LOCK_HANDLE)).thenReturn(true);
        lenient().when(aiReplyJobService.failTakenJob(anyLong(), anyInt(), any())).thenReturn(true);
        // 백그라운드 답변 생성을 호출한 스레드에서 바로 실행해 결과를 확인한다
        lenient().doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).when(replySlot).execute(any());
        aiChatFacade = createFacade(QUEUE_DISABLED, Set.of());
    }

    @Test
    void noRoom_startOrResumeConversation_startsSessionAndReturnsCreated() {
        given(aiChatRoomService.findOrReserveRoom(USER_ID)).willReturn(room(ROOM_ID, AiChatRoomStatus.PENDING, null));
        given(userService.findUserAiProfile(USER_ID)).willReturn(USER_AI_PROFILE);
        given(aiChatClient.startSession(USER_ID, ROOM_ID, USER_PROFILE_REQUEST, null)).willReturn(startSessionResponse(ROOM_ID));
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
    void storedMemory_startOrResumeConversation_sendsMemoryToAiServer() {
        given(aiChatRoomService.findOrReserveRoom(USER_ID)).willReturn(room(ROOM_ID, AiChatRoomStatus.PENDING, null));
        given(userService.findUserAiProfile(USER_ID)).willReturn(USER_AI_PROFILE);
        given(userService.findAiMemory(USER_ID)).willReturn(MEMORY);
        given(aiChatClient.startSession(USER_ID, ROOM_ID, USER_PROFILE_REQUEST, MEMORY))
                .willReturn(startSessionResponse(ROOM_ID));
        given(aiChatRoomService.activateRoom(ROOM_ID, GREETING, PURGE_AT))
                .willReturn(room(ROOM_ID, AiChatRoomStatus.ACTIVE,
                        LocalDateTime.now(ZoneOffset.UTC).plusMinutes(29)));

        AiConversationStartResult result = aiChatFacade.startOrResumeConversation(USER_ID);

        assertThat(result.isCreated()).isTrue();
        verify(aiChatClient, times(1)).startSession(any(), any(), any(), any());
    }

    @Test
    void memoryRejected_startOrResumeConversation_retriesOnceWithoutMemory() {
        given(aiChatRoomService.findOrReserveRoom(USER_ID)).willReturn(room(ROOM_ID, AiChatRoomStatus.PENDING, null));
        given(userService.findUserAiProfile(USER_ID)).willReturn(USER_AI_PROFILE);
        given(userService.findAiMemory(USER_ID)).willReturn(MEMORY);
        given(aiChatClient.startSession(USER_ID, ROOM_ID, USER_PROFILE_REQUEST, MEMORY))
                .willThrow(new BusinessException(AiChatErrorCode.AICHAT_MEMORY_REJECTED));
        given(aiChatClient.startSession(USER_ID, ROOM_ID, USER_PROFILE_REQUEST, null))
                .willReturn(startSessionResponse(ROOM_ID));
        given(aiChatRoomService.activateRoom(ROOM_ID, GREETING, PURGE_AT))
                .willReturn(room(ROOM_ID, AiChatRoomStatus.ACTIVE,
                        LocalDateTime.now(ZoneOffset.UTC).plusMinutes(29)));

        AiConversationStartResult result = aiChatFacade.startOrResumeConversation(USER_ID);

        assertThat(result.isCreated()).isTrue();
        verify(aiChatClient).startSession(USER_ID, ROOM_ID, USER_PROFILE_REQUEST, null);
        verify(aiChatRoomService, never()).discardRoom(anyLong());
    }

    @Test
    void retryWithoutMemoryFails_startOrResumeConversation_discardsRoom() {
        given(aiChatRoomService.findOrReserveRoom(USER_ID)).willReturn(room(ROOM_ID, AiChatRoomStatus.PENDING, null));
        given(userService.findUserAiProfile(USER_ID)).willReturn(USER_AI_PROFILE);
        given(userService.findAiMemory(USER_ID)).willReturn(MEMORY);
        given(aiChatClient.startSession(USER_ID, ROOM_ID, USER_PROFILE_REQUEST, MEMORY))
                .willThrow(new BusinessException(AiChatErrorCode.AICHAT_MEMORY_REJECTED));
        given(aiChatClient.startSession(USER_ID, ROOM_ID, USER_PROFILE_REQUEST, null))
                .willThrow(new BusinessException(AiChatErrorCode.AICHAT_REQUEST_REJECTED));

        assertThatThrownBy(() -> aiChatFacade.startOrResumeConversation(USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(AiChatErrorCode.AICHAT_REQUEST_REJECTED);
        verify(aiChatClient, times(2)).startSession(any(), any(), any(), any());
        verify(aiChatRoomService).discardRoom(ROOM_ID);
    }

    // 기억과 상관없는 실패는 기억을 빼고 보내도 같다 => 재시도하지 않는다
    @Test
    void aiServerUnavailableWithMemory_startOrResumeConversation_doesNotRetry() {
        given(aiChatRoomService.findOrReserveRoom(USER_ID)).willReturn(room(ROOM_ID, AiChatRoomStatus.PENDING, null));
        given(userService.findUserAiProfile(USER_ID)).willReturn(USER_AI_PROFILE);
        given(userService.findAiMemory(USER_ID)).willReturn(MEMORY);
        given(aiChatClient.startSession(USER_ID, ROOM_ID, USER_PROFILE_REQUEST, MEMORY))
                .willThrow(new BusinessException(AiChatErrorCode.AICHAT_SERVER_UNAVAILABLE));

        assertThatThrownBy(() -> aiChatFacade.startOrResumeConversation(USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(AiChatErrorCode.AICHAT_SERVER_UNAVAILABLE);
        verify(aiChatClient, times(1)).startSession(any(), any(), any(), any());
        verify(aiChatRoomService).discardRoom(ROOM_ID);
    }

    @Test
    void shortCooldownUser_startOrResumeConversation_usesShortCooldown() {
        aiChatFacade = createFacade(QUEUE_DISABLED, Set.of(SHORT_COOLDOWN_EXTERNAL_ID));
        given(userService.findAuthenticatedUser(USER_ID)).willReturn(new UserResponse(
                USER_ID, SHORT_COOLDOWN_EXTERNAL_ID, "테스트 사용자", null, true, null));
        given(aiChatRoomService.findOrReserveRoomWithoutDailyLimit(USER_ID, SHORT_COMPLETION_COOLDOWN))
                .willReturn(room(ROOM_ID, AiChatRoomStatus.PENDING, null));
        given(userService.findUserAiProfile(USER_ID)).willReturn(USER_AI_PROFILE);
        given(aiChatClient.startSession(USER_ID, ROOM_ID, USER_PROFILE_REQUEST, null)).willReturn(startSessionResponse(ROOM_ID));
        given(aiChatRoomService.activateRoom(ROOM_ID, GREETING, PURGE_AT))
                .willReturn(room(ROOM_ID, AiChatRoomStatus.ACTIVE, PURGE_AT));

        AiConversationStartResult result = aiChatFacade.startOrResumeConversation(USER_ID);

        assertThat(result.isCreated()).isTrue();
        verify(aiChatRoomService).findOrReserveRoomWithoutDailyLimit(USER_ID, SHORT_COMPLETION_COOLDOWN);
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
        given(aiChatRoomService.reserveRoom(USER_ID))
                .willReturn(room(NEW_ROOM_ID, AiChatRoomStatus.PENDING, null));
        given(userService.findUserAiProfile(USER_ID)).willReturn(USER_AI_PROFILE);
        given(aiChatClient.startSession(USER_ID, NEW_ROOM_ID, USER_PROFILE_REQUEST, null)).willReturn(startSessionResponse(NEW_ROOM_ID));
        given(aiChatRoomService.activateRoom(NEW_ROOM_ID, GREETING, PURGE_AT))
                .willReturn(room(NEW_ROOM_ID, AiChatRoomStatus.ACTIVE,
                        LocalDateTime.now(ZoneOffset.UTC).plusMinutes(29)));

        AiConversationStartResult result = aiChatFacade.startOrResumeConversation(USER_ID);

        assertThat(result.isCreated()).isTrue();
        assertThat(result.conversation().conversationId()).isEqualTo(NEW_ROOM_ID);
        assertThat(result.conversation().progress()).isZero();
        verify(aiChatRoomService).expireRoom(ROOM_ID);
    }

    @Test
    void analyzingRoomPastPurgeAt_startOrResumeConversation_expiresRoomAndCreatesNewConversation() {
        given(aiChatRoomService.findOrReserveRoom(USER_ID))
                .willReturn(room(ROOM_ID, AiChatRoomStatus.ANALYZING,
                        LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1)));
        given(aiChatRoomService.reserveRoom(USER_ID))
                .willReturn(room(NEW_ROOM_ID, AiChatRoomStatus.PENDING, null));
        given(userService.findUserAiProfile(USER_ID)).willReturn(USER_AI_PROFILE);
        given(aiChatClient.startSession(USER_ID, NEW_ROOM_ID, USER_PROFILE_REQUEST, null)).willReturn(startSessionResponse(NEW_ROOM_ID));
        given(aiChatRoomService.activateRoom(NEW_ROOM_ID, GREETING, PURGE_AT))
                .willReturn(room(NEW_ROOM_ID, AiChatRoomStatus.ACTIVE,
                        LocalDateTime.now(ZoneOffset.UTC).plusMinutes(29)));

        AiConversationStartResult result = aiChatFacade.startOrResumeConversation(USER_ID);

        assertThat(result.isCreated()).isTrue();
        assertThat(result.conversation().conversationId()).isEqualTo(NEW_ROOM_ID);
        verify(aiChatRoomService).expireRoom(ROOM_ID);
    }

    @Test
    void roomPastPurgeAtOverDailyLimit_startOrResumeConversation_expiresRoomWithoutStartingSession() {
        given(aiChatRoomService.findOrReserveRoom(USER_ID))
                .willReturn(room(ROOM_ID, AiChatRoomStatus.ACTIVE,
                        LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1)));
        given(aiChatRoomService.reserveRoom(USER_ID))
                .willThrow(new BusinessException(AiChatErrorCode.AICHAT_DAILY_CONVERSATION_LIMIT_EXCEEDED));

        assertThatThrownBy(() -> aiChatFacade.startOrResumeConversation(USER_ID))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(AiChatErrorCode.AICHAT_DAILY_CONVERSATION_LIMIT_EXCEEDED));
        verify(aiChatRoomService).expireRoom(ROOM_ID);
        verifyNoInteractions(aiChatClient);
    }

    @Test
    void shortCooldownUserRoomPastPurgeAt_startOrResumeConversation_reservesRoomWithoutDailyLimit() {
        aiChatFacade = createFacade(QUEUE_DISABLED, Set.of(SHORT_COOLDOWN_EXTERNAL_ID));
        given(userService.findAuthenticatedUser(USER_ID)).willReturn(new UserResponse(
                USER_ID, SHORT_COOLDOWN_EXTERNAL_ID, "테스트 사용자", null, true, null));
        given(aiChatRoomService.findOrReserveRoomWithoutDailyLimit(USER_ID, SHORT_COMPLETION_COOLDOWN))
                .willReturn(room(ROOM_ID, AiChatRoomStatus.ACTIVE,
                        LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1)));
        given(aiChatRoomService.reserveRoomWithoutDailyLimit(USER_ID))
                .willReturn(room(NEW_ROOM_ID, AiChatRoomStatus.PENDING, null));
        given(userService.findUserAiProfile(USER_ID)).willReturn(USER_AI_PROFILE);
        given(aiChatClient.startSession(USER_ID, NEW_ROOM_ID, USER_PROFILE_REQUEST, null))
                .willReturn(startSessionResponse(NEW_ROOM_ID));
        given(aiChatRoomService.activateRoom(NEW_ROOM_ID, GREETING, PURGE_AT))
                .willReturn(room(NEW_ROOM_ID, AiChatRoomStatus.ACTIVE, PURGE_AT));

        AiConversationStartResult result = aiChatFacade.startOrResumeConversation(USER_ID);

        assertThat(result.conversation().conversationId()).isEqualTo(NEW_ROOM_ID);
        verify(aiChatRoomService).expireRoom(ROOM_ID);
        verify(aiChatRoomService, never()).reserveRoom(anyLong());
    }

    @Test
    void dailyLimitExceeded_startOrResumeConversation_doesNotCallAiServer() {
        given(aiChatRoomService.findOrReserveRoom(USER_ID))
                .willThrow(new BusinessException(AiChatErrorCode.AICHAT_DAILY_CONVERSATION_LIMIT_EXCEEDED));

        assertThatThrownBy(() -> aiChatFacade.startOrResumeConversation(USER_ID))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(AiChatErrorCode.AICHAT_DAILY_CONVERSATION_LIMIT_EXCEEDED));
        verifyNoInteractions(aiChatClient);
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
        verify(aiChatRoomService, never()).expireRoom(anyLong());
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
        verify(aiChatRoomService, never()).expireRoom(anyLong());
    }

    @Test
    void aiServerUnavailableWhileStarting_startOrResumeConversation_discardsReservedRoom() {
        given(aiChatRoomService.findOrReserveRoom(USER_ID)).willReturn(room(ROOM_ID, AiChatRoomStatus.PENDING, null));
        given(userService.findUserAiProfile(USER_ID)).willReturn(USER_AI_PROFILE);
        given(aiChatClient.startSession(USER_ID, ROOM_ID, USER_PROFILE_REQUEST, null))
                .willThrow(new BusinessException(AiChatErrorCode.AICHAT_REQUEST_TIMEOUT));

        assertThatThrownBy(() -> aiChatFacade.startOrResumeConversation(USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(AiChatErrorCode.AICHAT_REQUEST_TIMEOUT);
        verify(aiChatRoomService).discardRoom(ROOM_ID);
        verify(aiChatRoomService, never()).activateRoom(anyLong(), any(), any());
    }

    @Test
    void userProfileLookupFails_startOrResumeConversation_discardsRoomWithoutCallingAiServer() {
        given(aiChatRoomService.findOrReserveRoom(USER_ID)).willReturn(room(ROOM_ID, AiChatRoomStatus.PENDING, null));
        given(userService.findUserAiProfile(USER_ID))
                .willThrow(new BusinessException(UserErrorCode.USER_NOT_FOUND));

        assertThatThrownBy(() -> aiChatFacade.startOrResumeConversation(USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(UserErrorCode.USER_NOT_FOUND);
        verify(aiChatRoomService).discardRoom(ROOM_ID);
        verifyNoInteractions(aiChatClient);
    }

    @Test
    void aiResponseWithoutGreeting_startOrResumeConversation_throwsInvalidResponseAndDiscardsRoom() {
        given(aiChatRoomService.findOrReserveRoom(USER_ID)).willReturn(room(ROOM_ID, AiChatRoomStatus.PENDING, null));
        given(userService.findUserAiProfile(USER_ID)).willReturn(USER_AI_PROFILE);
        given(aiChatClient.startSession(USER_ID, ROOM_ID, USER_PROFILE_REQUEST, null))
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
        given(userService.findUserAiProfile(USER_ID)).willReturn(USER_AI_PROFILE);
        given(aiChatClient.startSession(USER_ID, ROOM_ID, USER_PROFILE_REQUEST, null))
                .willReturn(startSessionResponse(NEW_ROOM_ID));

        assertThatThrownBy(() -> aiChatFacade.startOrResumeConversation(USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(AiChatErrorCode.AICHAT_INVALID_RESPONSE);
        verify(aiChatRoomService).discardRoom(ROOM_ID);
        verify(aiChatRoomService, never()).activateRoom(anyLong(), any(), any());
    }

    @Test
    void aiConversationNotAgreed_startOrResumeConversation_throwsConsentRequiredWithoutReservingRoom() {
        given(consentService.hasAgreedToAiConversation(USER_ID)).willReturn(false);

        assertThatThrownBy(() -> aiChatFacade.startOrResumeConversation(USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(AiChatErrorCode.AICHAT_CONSENT_REQUIRED);
        verifyNoInteractions(aiChatRoomService, aiChatClient);
    }

    @Test
    void aiConversationNotAgreed_sendMessage_throwsConsentRequiredWithoutSavingMessage() {
        given(consentService.hasAgreedToAiConversation(USER_ID)).willReturn(false);

        assertThatThrownBy(() -> aiChatFacade.sendMessage(USER_ID, ROOM_ID, sendMessageRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(AiChatErrorCode.AICHAT_CONSENT_REQUIRED);
        verifyNoInteractions(aiChatRoomService, aiMessageService, aiReplyJobService, aiConversationLock,
                aiReplyTaskExecutor, aiChatClient);
    }

    @Test
    void streamableConversation_connectStream_doesNotCheckConsent() {
        AiChatRoom room = room(ROOM_ID, AiChatRoomStatus.ACTIVE, PURGE_AT);
        given(aiChatRoomService.findStreamableRoom(USER_ID, ROOM_ID)).willReturn(room);
        given(aiChatStreamService.connect(room)).willReturn(new SseEmitter());

        aiChatFacade.connectStream(USER_ID, ROOM_ID);

        verifyNoInteractions(consentService);
    }

    @Test
    void newClientMessageId_sendMessage_returnsAcceptedAndSavesReplyInBackground() {
        givenNewMessage();
        given(streamMessageCall())
                .willAnswer(streamReply(sendMessageResponse(AI_CONTENT)));
        given(aiReplyJobService.commitReply(JOB_ID, ATTEMPT, AI_CONTENT, 5, false, PURGE_AT))
                .willReturn(AiReplyCommitResult.saved(message(AI_MESSAGE_ID, USER_MESSAGE_ID)));

        AiMessageSendResult result = aiChatFacade.sendMessage(USER_ID, ROOM_ID, sendMessageRequest());

        assertThat(result.isAccepted()).isTrue();
        assertThat(result.accepted()).isEqualTo(new AiMessageAcceptedResponse(USER_MESSAGE_ID, CLIENT_MESSAGE_ID));
        verify(aiReplyJobService).commitReply(JOB_ID, ATTEMPT, AI_CONTENT, 5, false, PURGE_AT);
        verify(aiReplyJobService, never()).failTakenJob(anyLong(), anyInt(), any());
    }

    @Test
    void inputLocked_sendMessage_publishesLockedReplyWithoutRequestingAnalysis() {
        givenNewMessage();
        given(streamMessageCall())
                .willAnswer(streamReply(sendMessageResponse(AI_CONTENT, true)));
        given(aiReplyJobService.commitReply(JOB_ID, ATTEMPT, AI_CONTENT, 5, true, PURGE_AT))
                .willReturn(AiReplyCommitResult.saved(message(AI_MESSAGE_ID, USER_MESSAGE_ID, true)));

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
        verify(aiReplyJobService, never()).createUserMessageAndJob(anyLong(), anyLong(), any(), any(), any());
    }

    @Test
    void replyJobInProgress_sendMessage_returnsSameAcceptedWithoutRegenerating() {
        given(aiMessageService.findUserMessage(ROOM_ID, CLIENT_MESSAGE_ID))
                .willReturn(Optional.of(message(USER_MESSAGE_ID, null)));
        given(aiMessageService.findReply(USER_MESSAGE_ID)).willReturn(Optional.empty());
        given(aiReplyJobService.isReplyInProgress(USER_MESSAGE_ID)).willReturn(true);

        AiMessageSendResult result = aiChatFacade.sendMessage(USER_ID, ROOM_ID, sendMessageRequest());

        assertThat(result.accepted()).isEqualTo(new AiMessageAcceptedResponse(USER_MESSAGE_ID, CLIENT_MESSAGE_ID));
        verify(aiConversationLock, never()).tryLock(anyLong());
        verify(aiGenerationStateService, never()).startGenerating(anyLong(), anyLong());
        verifyNoInteractions(aiChatClient, aiReplyTaskExecutor);
    }

    @Test
    void unansweredMessageWithoutJobInProgress_sendMessage_restartsJobAndRegeneratesReply() {
        AiMessage userMessage = message(USER_MESSAGE_ID, null);
        given(aiMessageService.findUserMessage(ROOM_ID, CLIENT_MESSAGE_ID)).willReturn(Optional.of(userMessage));
        given(aiMessageService.findReply(USER_MESSAGE_ID)).willReturn(Optional.empty());
        given(aiReplyJobService.isReplyInProgress(USER_MESSAGE_ID)).willReturn(false);
        given(aiReplyJobService.restartJob(ROOM_ID, userMessage, USER_ID, LOCK_TOKEN))
                .willReturn(new AiReplyJobDispatch(JOB_ID, DISPATCH_TOKEN, userMessage));
        givenJobTaken(userMessage);
        given(streamMessageCall())
                .willAnswer(streamReply(sendMessageResponse(AI_CONTENT)));
        given(aiReplyJobService.commitReply(JOB_ID, ATTEMPT, AI_CONTENT, 5, false, PURGE_AT))
                .willReturn(AiReplyCommitResult.saved(message(AI_MESSAGE_ID, USER_MESSAGE_ID)));

        AiMessageSendResult result = aiChatFacade.sendMessage(USER_ID, ROOM_ID, sendMessageRequest());

        assertThat(result.accepted()).isEqualTo(new AiMessageAcceptedResponse(USER_MESSAGE_ID, CLIENT_MESSAGE_ID));
        verify(aiReplyJobService, never()).createUserMessageAndJob(anyLong(), anyLong(), any(), any(), any());
        var order = inOrder(aiReplyJobService, aiChatClient);
        order.verify(aiReplyJobService).restartJob(ROOM_ID, userMessage, USER_ID, LOCK_TOKEN);
        order.verify(aiReplyJobService).take(JOB_ID, DISPATCH_TOKEN, 1L, null);
        order.verify(aiChatClient).streamMessage(eq(USER_ID), eq(ROOM_ID), eq(USER_MESSAGE_ID), eq(USER_CONTENT), any());
        order.verify(aiReplyJobService).commitReply(JOB_ID, ATTEMPT, AI_CONTENT, 5, false, PURGE_AT);
    }

    @Test
    void replySavedWhileAcquiringLock_sendMessage_returnsSavedReplyAndReleasesLock() {
        given(aiMessageService.findUserMessage(ROOM_ID, CLIENT_MESSAGE_ID))
                .willReturn(Optional.of(message(USER_MESSAGE_ID, null)));
        given(aiMessageService.findReply(USER_MESSAGE_ID))
                .willReturn(Optional.empty(), Optional.of(message(AI_MESSAGE_ID, USER_MESSAGE_ID)));
        given(aiReplyJobService.isReplyInProgress(USER_MESSAGE_ID)).willReturn(false);

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
        given(aiReplyJobService.commitReply(JOB_ID, ATTEMPT, AI_CONTENT, 5, false, PURGE_AT))
                .willReturn(AiReplyCommitResult.saved(message(AI_MESSAGE_ID, USER_MESSAGE_ID)));

        aiChatFacade.sendMessage(USER_ID, ROOM_ID, sendMessageRequest());

        var order = inOrder(aiReplyTaskExecutor, aiReplyJobService, aiGenerationStateService, replySlot,
                aiChatClient, aiConversationLock);
        order.verify(aiReplyTaskExecutor).tryReserve();
        order.verify(aiReplyJobService).createUserMessageAndJob(ROOM_ID, USER_ID, CLIENT_MESSAGE_ID, USER_CONTENT, LOCK_TOKEN);
        order.verify(aiGenerationStateService).startGenerating(ROOM_ID, USER_MESSAGE_ID);
        order.verify(replySlot).execute(any());
        order.verify(aiReplyJobService).take(JOB_ID, DISPATCH_TOKEN, 1L, null);
        order.verify(aiChatClient).streamMessage(eq(USER_ID), eq(ROOM_ID), eq(USER_MESSAGE_ID), eq(USER_CONTENT), any());
        order.verify(aiReplyJobService).commitReply(JOB_ID, ATTEMPT, AI_CONTENT, 5, false, PURGE_AT);
        order.verify(aiGenerationStateService).finishGenerating(ROOM_ID, USER_MESSAGE_ID);
        order.verify(aiConversationLock).unlock(LOCK_HANDLE);
    }

    @Test
    void newClientMessageId_sendMessage_publishesTurnEventsAndEndsTurnAfterUnlock() {
        givenNewMessage();
        given(streamMessageCall())
                .willAnswer(streamReply(sendMessageResponse(AI_CONTENT)));
        given(aiReplyJobService.commitReply(JOB_ID, ATTEMPT, AI_CONTENT, 5, false, PURGE_AT))
                .willReturn(AiReplyCommitResult.saved(message(AI_MESSAGE_ID, USER_MESSAGE_ID)));

        aiChatFacade.sendMessage(USER_ID, ROOM_ID, sendMessageRequest());

        var order = inOrder(aiChatEventPublisher, aiChatClient, aiReplyJobService, aiConversationLock);
        order.verify(aiChatEventPublisher).publish(ROOM_ID, AiChatEventType.ACCEPTED,
                new AiChatAcceptedEvent(USER_MESSAGE_ID, CLIENT_MESSAGE_ID));
        order.verify(aiChatClient).streamMessage(eq(USER_ID), eq(ROOM_ID), eq(USER_MESSAGE_ID), eq(USER_CONTENT), any());
        order.verify(aiChatEventPublisher).publish(ROOM_ID, AiChatEventType.DELTA,
                new AiChatDeltaEvent(USER_MESSAGE_ID, 0, AI_CONTENT));
        order.verify(aiReplyJobService).commitReply(JOB_ID, ATTEMPT, AI_CONTENT, 5, false, PURGE_AT);
        order.verify(aiConversationLock).unlock(LOCK_HANDLE);
        order.verify(aiChatEventPublisher).publish(ROOM_ID, AiChatEventType.COMPLETED,
                new AiChatCompletedEvent(USER_MESSAGE_ID, AI_MESSAGE_ID, AI_CONTENT, 5, false));
        order.verify(aiChatEventPublisher).publish(ROOM_ID, AiChatEventType.DONE,
                new AiChatDoneEvent(USER_MESSAGE_ID));
    }

    @Test
    void replyAlreadySaved_sendMessage_publishesSavedReplyAsCompleted() {
        givenNewMessage();
        given(streamMessageCall())
                .willAnswer(streamReply(sendMessageResponse(OTHER_AI_CONTENT)));
        given(aiReplyJobService.commitReply(JOB_ID, ATTEMPT, OTHER_AI_CONTENT, 5, false, PURGE_AT))
                .willThrow(new DataIntegrityViolationException("uk_ai_messages_reply_to_message_id"));
        given(aiMessageService.findReply(USER_MESSAGE_ID))
                .willReturn(Optional.of(message(AI_MESSAGE_ID, USER_MESSAGE_ID)));

        aiChatFacade.sendMessage(USER_ID, ROOM_ID, sendMessageRequest());

        var order = inOrder(aiChatEventPublisher, aiConversationLock);
        order.verify(aiConversationLock).unlock(LOCK_HANDLE);
        order.verify(aiChatEventPublisher).publish(ROOM_ID, AiChatEventType.COMPLETED,
                new AiChatCompletedEvent(USER_MESSAGE_ID, AI_MESSAGE_ID, AI_CONTENT, 5, false));
        order.verify(aiChatEventPublisher).publish(ROOM_ID, AiChatEventType.DONE,
                new AiChatDoneEvent(USER_MESSAGE_ID));
        verify(aiChatEventPublisher, never()).publish(eq(ROOM_ID), eq(AiChatEventType.ERROR), any());
        verify(aiGenerationStateService).finishGenerating(ROOM_ID, USER_MESSAGE_ID);
    }

    @Test
    void integrityViolationWithoutSavedReply_sendMessage_publishesErrorAndReleasesLock() {
        givenNewMessage();
        given(streamMessageCall())
                .willAnswer(streamReply(sendMessageResponse(AI_CONTENT)));
        given(aiReplyJobService.commitReply(JOB_ID, ATTEMPT, AI_CONTENT, 5, false, PURGE_AT))
                .willThrow(new DataIntegrityViolationException("fk_ai_messages_room"));
        given(aiMessageService.findReply(USER_MESSAGE_ID)).willReturn(Optional.empty());

        aiChatFacade.sendMessage(USER_ID, ROOM_ID, sendMessageRequest());

        verify(aiChatEventPublisher).publish(ROOM_ID, AiChatEventType.ERROR, new AiChatErrorEvent(
                USER_MESSAGE_ID, CommonErrorCode.COMMON_INTERNAL_SERVER_ERROR.getMessage(), true, false));
        verify(aiChatEventPublisher, never()).publish(eq(ROOM_ID), eq(AiChatEventType.COMPLETED), any());
        verify(aiConversationLock).unlock(LOCK_HANDLE);
        verify(aiReplyJobService).failTakenJob(JOB_ID, ATTEMPT, CommonErrorCode.COMMON_INTERNAL_SERVER_ERROR.name());
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
        verify(aiReplyJobService, never()).commitReply(anyLong(), anyInt(), any(), anyInt(), anyBoolean(), any());
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
        given(aiReplyJobService.commitReply(JOB_ID, ATTEMPT, AI_CONTENT, 5, false, PURGE_AT))
                .willReturn(AiReplyCommitResult.saved(message(AI_MESSAGE_ID, USER_MESSAGE_ID)));
        willThrow(new IllegalStateException("redis down"))
                .given(aiChatEventPublisher).publish(eq(ROOM_ID), eq(AiChatEventType.ACCEPTED), any());

        AiMessageSendResult result = aiChatFacade.sendMessage(USER_ID, ROOM_ID, sendMessageRequest());

        assertThat(result.isAccepted()).isTrue();
        verify(aiReplyJobService).commitReply(JOB_ID, ATTEMPT, AI_CONTENT, 5, false, PURGE_AT);
        verify(aiChatEventPublisher, times(1)).publish(anyLong(), any(), any());
    }

    @Test
    void replyQueueEnabled_sendMessage_enqueuesJobAndKeepsLockWithoutAiCall() {
        aiChatFacade = createFacade(QUEUE_ENABLED, Set.of());
        givenNewMessage();

        AiMessageSendResult result = aiChatFacade.sendMessage(USER_ID, ROOM_ID, sendMessageRequest());

        assertThat(result.accepted()).isEqualTo(new AiMessageAcceptedResponse(USER_MESSAGE_ID, CLIENT_MESSAGE_ID));
        var order = inOrder(aiReplyJobService, aiGenerationStateService, aiReplyQueueService);
        order.verify(aiReplyJobService).createUserMessageAndJob(ROOM_ID, USER_ID, CLIENT_MESSAGE_ID, USER_CONTENT, LOCK_TOKEN);
        order.verify(aiGenerationStateService).startGenerating(ROOM_ID, USER_MESSAGE_ID);
        order.verify(aiReplyQueueService).enqueue(JOB_ID, DISPATCH_TOKEN);
        verify(aiConversationLock, never()).unlock(any());
        verifyNoInteractions(aiReplyTaskExecutor, aiChatClient, aiChatEventPublisher);
    }

    @Test
    void replyQueueEnabledAndUnansweredMessage_sendMessage_restartsJobAndEnqueuesIt() {
        aiChatFacade = createFacade(QUEUE_ENABLED, Set.of());
        AiMessage userMessage = message(USER_MESSAGE_ID, null);
        given(aiMessageService.findUserMessage(ROOM_ID, CLIENT_MESSAGE_ID)).willReturn(Optional.of(userMessage));
        given(aiMessageService.findReply(USER_MESSAGE_ID)).willReturn(Optional.empty());
        given(aiReplyJobService.restartJob(ROOM_ID, userMessage, USER_ID, LOCK_TOKEN))
                .willReturn(new AiReplyJobDispatch(JOB_ID, DISPATCH_TOKEN, userMessage));

        AiMessageSendResult result = aiChatFacade.sendMessage(USER_ID, ROOM_ID, sendMessageRequest());

        assertThat(result.isAccepted()).isTrue();
        verify(aiReplyQueueService).enqueue(JOB_ID, DISPATCH_TOKEN);
        verifyNoInteractions(aiReplyTaskExecutor, aiChatClient);
    }

    @Test
    void replyQueueEnabledAndJobInProgressInConversation_sendMessage_throwsTooManyRequestsWithoutEnqueue() {
        aiChatFacade = createFacade(QUEUE_ENABLED, Set.of());
        given(aiMessageService.findUserMessage(ROOM_ID, CLIENT_MESSAGE_ID)).willReturn(Optional.empty());
        given(aiReplyJobService.existsReplyInProgress(ROOM_ID)).willReturn(true);

        assertThatThrownBy(() -> aiChatFacade.sendMessage(USER_ID, ROOM_ID, sendMessageRequest()))
                .isInstanceOf(TooManyRequestsException.class);
        verify(aiConversationLock).unlock(LOCK_HANDLE);
        verifyNoInteractions(aiReplyQueueService);
    }

    @Test
    void jobTaken_replyToJob_attachesLeaseTargetAndReturnsFinished() {
        givenJobTaken(message(USER_MESSAGE_ID, null));
        given(streamMessageCall()).willAnswer(streamReply(sendMessageResponse(AI_CONTENT)));
        given(aiReplyJobService.commitReply(JOB_ID, ATTEMPT, AI_CONTENT, 5, false, PURGE_AT))
                .willReturn(AiReplyCommitResult.saved(message(AI_MESSAGE_ID, USER_MESSAGE_ID)));
        AiReplyLease lease = AiReplyLease.start(Clock.systemUTC());

        AiReplyJobOutcome outcome = aiChatFacade.replyToJob(JOB_ID, DISPATCH_TOKEN, 1L, null, lease);

        assertThat(outcome).isEqualTo(AiReplyJobOutcome.FINISHED);
        assertThat(lease.target()).contains(new AiReplyLease.Target(ROOM_ID, USER_MESSAGE_ID, LOCK_TOKEN));
    }

    @Test
    void leaseLostDuringReply_replyToJob_stopsWithoutRecordingFailureOrUnlock() {
        givenJobTaken(message(USER_MESSAGE_ID, null));
        AiReplyLease lease = AiReplyLease.start(Clock.systemUTC());
        given(streamMessageCall()).willAnswer(invocation -> {
            lease.markLost();
            invocation.<Consumer<String>>getArgument(4).accept(AI_CONTENT);
            return sendMessageResponse(AI_CONTENT);
        });

        AiReplyJobOutcome outcome = aiChatFacade.replyToJob(JOB_ID, DISPATCH_TOKEN, 1L, null, lease);

        assertThat(outcome).isEqualTo(AiReplyJobOutcome.OWNERSHIP_LOST);
        verify(aiReplyJobService, never()).failTakenJob(anyLong(), anyInt(), any());
        verify(aiReplyJobService, never()).commitReply(anyLong(), anyInt(), any(), anyInt(), anyBoolean(), any());
        verify(aiChatEventPublisher, never()).publish(eq(ROOM_ID), eq(AiChatEventType.DELTA), any());
        verify(aiChatEventPublisher, never()).publish(eq(ROOM_ID), eq(AiChatEventType.ERROR), any());
        verify(aiConversationLock, never()).unlock(any());
    }

    @Test
    void commitRejectedByNewerAttempt_replyToJob_returnsOwnershipLost() {
        givenJobTaken(message(USER_MESSAGE_ID, null));
        given(streamMessageCall()).willAnswer(streamReply(sendMessageResponse(AI_CONTENT)));
        given(aiReplyJobService.commitReply(JOB_ID, ATTEMPT, AI_CONTENT, 5, false, PURGE_AT))
                .willReturn(new AiReplyCommitResult(null));

        assertThat(aiChatFacade.replyToJob(JOB_ID, DISPATCH_TOKEN, 1L, null, AiReplyLease.unmanaged()))
                .isEqualTo(AiReplyJobOutcome.OWNERSHIP_LOST);
    }

    @Test
    void failureRecordRejected_replyToJob_returnsOwnershipLost() {
        givenJobTaken(message(USER_MESSAGE_ID, null));
        given(streamMessageCall()).willThrow(new BusinessException(AiChatErrorCode.AICHAT_SERVER_UNAVAILABLE));
        given(aiReplyJobService.failTakenJob(JOB_ID, ATTEMPT, AiChatErrorCode.AICHAT_SERVER_UNAVAILABLE.name()))
                .willReturn(false);

        assertThat(aiChatFacade.replyToJob(JOB_ID, DISPATCH_TOKEN, 1L, null, AiReplyLease.unmanaged()))
                .isEqualTo(AiReplyJobOutcome.OWNERSHIP_LOST);
    }

    @Test
    void replyFailedAndRecorded_replyToJob_returnsFinished() {
        givenJobTaken(message(USER_MESSAGE_ID, null));
        given(streamMessageCall()).willThrow(new BusinessException(AiChatErrorCode.AICHAT_SERVER_UNAVAILABLE));

        assertThat(aiChatFacade.replyToJob(JOB_ID, DISPATCH_TOKEN, 1L, null, AiReplyLease.unmanaged()))
                .isEqualTo(AiReplyJobOutcome.FINISHED);
        verify(aiConversationLock).unlock(LOCK_HANDLE);
    }

    @Test
    void staleJob_replyToJob_returnsFinished() {
        givenTakeResult(finishedTakeResult(AiReplyJobTakeResult.Outcome.STALE));

        assertThat(aiChatFacade.replyToJob(JOB_ID, DISPATCH_TOKEN, 1L, null, AiReplyLease.unmanaged()))
                .isEqualTo(AiReplyJobOutcome.FINISHED);
    }

    @Test
    void conversationAlreadyLocked_sendMessage_throwsTooManyRequests() {
        given(aiMessageService.findUserMessage(ROOM_ID, CLIENT_MESSAGE_ID)).willReturn(Optional.empty());
        given(aiConversationLock.tryLock(ROOM_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> aiChatFacade.sendMessage(USER_ID, ROOM_ID, sendMessageRequest()))
                .isInstanceOf(TooManyRequestsException.class);
        verify(aiReplyJobService, never()).createUserMessageAndJob(anyLong(), anyLong(), any(), any(), any());
        verifyNoInteractions(aiChatClient, aiReplyTaskExecutor);
    }

    @Test
    void replyCapacityExceeded_sendMessage_throwsServiceUnavailableWithoutSavingMessage() {
        given(aiMessageService.findUserMessage(ROOM_ID, CLIENT_MESSAGE_ID)).willReturn(Optional.empty());
        given(aiReplyTaskExecutor.tryReserve()).willReturn(Optional.empty());

        assertThatThrownBy(() -> aiChatFacade.sendMessage(USER_ID, ROOM_ID, sendMessageRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(CommonErrorCode.COMMON_SERVICE_UNAVAILABLE);
        verify(aiReplyJobService, never()).createUserMessageAndJob(anyLong(), anyLong(), any(), any(), any());
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
        verify(aiReplyJobService).failJob(USER_MESSAGE_ID, CommonErrorCode.COMMON_SERVICE_UNAVAILABLE.name());
        verify(replySlot).release();
        verify(aiConversationLock).unlock(LOCK_HANDLE);
        verifyNoInteractions(aiChatClient, aiChatEventPublisher);
    }

    @Test
    void duplicateInsertFromAnotherInstance_sendMessage_throwsTooManyRequestsWithoutEvents() {
        given(aiMessageService.findUserMessage(ROOM_ID, CLIENT_MESSAGE_ID)).willReturn(Optional.empty());
        given(aiReplyJobService.createUserMessageAndJob(ROOM_ID, USER_ID, CLIENT_MESSAGE_ID, USER_CONTENT, LOCK_TOKEN))
                .willThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> aiChatFacade.sendMessage(USER_ID, ROOM_ID, sendMessageRequest()))
                .isInstanceOf(TooManyRequestsException.class);
        verify(replySlot).release();
        verify(aiConversationLock).unlock(LOCK_HANDLE);
        verifyNoInteractions(aiChatClient, aiChatEventPublisher);
    }

    @Test
    void replyJobInProgressInConversation_sendMessage_throwsTooManyRequestsAndReleasesLock() {
        given(aiMessageService.findUserMessage(ROOM_ID, CLIENT_MESSAGE_ID)).willReturn(Optional.empty());
        given(aiReplyJobService.existsReplyInProgress(ROOM_ID)).willReturn(true);

        assertThatThrownBy(() -> aiChatFacade.sendMessage(USER_ID, ROOM_ID, sendMessageRequest()))
                .isInstanceOf(TooManyRequestsException.class);
        verify(aiConversationLock).unlock(LOCK_HANDLE);
        verify(aiReplyJobService, never()).createUserMessageAndJob(anyLong(), anyLong(), any(), any(), any());
        verifyNoInteractions(aiReplyTaskExecutor, aiChatClient);
    }

    @Test
    void replyFailed_sendMessage_recordsJobFailedWithErrorCodeBeforeUnlock() {
        givenNewMessage();
        given(streamMessageCall())
                .willThrow(new BusinessException(AiChatErrorCode.AICHAT_SERVER_UNAVAILABLE));

        aiChatFacade.sendMessage(USER_ID, ROOM_ID, sendMessageRequest());

        var order = inOrder(aiReplyJobService, aiConversationLock);
        order.verify(aiReplyJobService).take(JOB_ID, DISPATCH_TOKEN, 1L, null);
        order.verify(aiReplyJobService).failTakenJob(JOB_ID, ATTEMPT, AiChatErrorCode.AICHAT_SERVER_UNAVAILABLE.name());
        order.verify(aiConversationLock).unlock(LOCK_HANDLE);
        verify(aiReplyJobService, never()).commitReply(anyLong(), anyInt(), any(), anyInt(), anyBoolean(), any());
    }

    @Test
    void jobFailureRecordFails_sendMessage_stillReleasesLockAndPublishesError() {
        givenNewMessage();
        given(streamMessageCall())
                .willThrow(new BusinessException(AiChatErrorCode.AICHAT_SERVER_UNAVAILABLE));
        willThrow(new IllegalStateException("db down")).given(aiReplyJobService).failTakenJob(anyLong(), anyInt(), any());

        AiMessageSendResult result = aiChatFacade.sendMessage(USER_ID, ROOM_ID, sendMessageRequest());

        assertThat(result.isAccepted()).isTrue();
        verify(aiConversationLock).unlock(LOCK_HANDLE);
        verify(aiChatEventPublisher).publish(ROOM_ID, AiChatEventType.ERROR, new AiChatErrorEvent(
                USER_MESSAGE_ID, AiChatErrorCode.AICHAT_SERVER_UNAVAILABLE.getMessage(), true, false));
    }

    @Test
    void jobTakeFails_sendMessage_recordsFailureAndReleasesLockWithoutAiCall() {
        givenNewMessage();
        given(aiReplyJobService.take(JOB_ID, DISPATCH_TOKEN, 1L, null))
                .willThrow(new IllegalStateException("db down"));

        AiMessageSendResult result = aiChatFacade.sendMessage(USER_ID, ROOM_ID, sendMessageRequest());

        assertThat(result.isAccepted()).isTrue();
        verifyNoInteractions(aiChatClient, aiChatEventPublisher);
        verify(aiReplyJobService).failJob(USER_MESSAGE_ID, CommonErrorCode.COMMON_INTERNAL_SERVER_ERROR.name());
        verify(aiGenerationStateService).finishGenerating(ROOM_ID, USER_MESSAGE_ID);
        verify(aiConversationLock).unlock(LOCK_HANDLE);
    }

    @Test
    void commitRejectedByNewerAttempt_sendMessage_leavesLockAndEndEventsToNewOwner() {
        givenNewMessage();
        given(streamMessageCall())
                .willAnswer(streamReply(sendMessageResponse(AI_CONTENT)));
        given(aiReplyJobService.commitReply(JOB_ID, ATTEMPT, AI_CONTENT, 5, false, PURGE_AT))
                .willReturn(new AiReplyCommitResult(null));

        aiChatFacade.sendMessage(USER_ID, ROOM_ID, sendMessageRequest());

        verify(aiChatEventPublisher, never()).publish(eq(ROOM_ID), eq(AiChatEventType.COMPLETED), any());
        verify(aiChatEventPublisher, never()).publish(eq(ROOM_ID), eq(AiChatEventType.ERROR), any());
        verify(aiChatEventPublisher, never()).publish(eq(ROOM_ID), eq(AiChatEventType.DONE), any());
        verify(aiGenerationStateService, never()).finishGenerating(anyLong(), anyLong());
        verify(aiConversationLock, never()).unlock(any());
    }

    @Test
    void replyFailedAfterJobTakenByNewerAttempt_sendMessage_leavesLockAndEndEventsToNewOwner() {
        givenNewMessage();
        given(streamMessageCall())
                .willThrow(new BusinessException(AiChatErrorCode.AICHAT_SERVER_UNAVAILABLE));
        given(aiReplyJobService.failTakenJob(JOB_ID, ATTEMPT, AiChatErrorCode.AICHAT_SERVER_UNAVAILABLE.name()))
                .willReturn(false);

        aiChatFacade.sendMessage(USER_ID, ROOM_ID, sendMessageRequest());

        verify(aiChatEventPublisher, never()).publish(eq(ROOM_ID), eq(AiChatEventType.ERROR), any());
        verify(aiChatEventPublisher, never()).publish(eq(ROOM_ID), eq(AiChatEventType.DONE), any());
        verify(aiGenerationStateService, never()).finishGenerating(anyLong(), anyLong());
        verify(aiConversationLock, never()).unlock(any());
    }

    @Test
    void conversationLockHeldByAnotherTurn_sendMessage_failsJobWithoutAiCallOrUnlock() {
        givenNewMessage();
        given(aiConversationLock.ensure(LOCK_HANDLE)).willReturn(false);

        aiChatFacade.sendMessage(USER_ID, ROOM_ID, sendMessageRequest());

        verify(aiReplyJobService).failTakenJob(JOB_ID, ATTEMPT, "AICHAT_REPLY_LOCK_LOST");
        verifyNoInteractions(aiChatClient, aiChatEventPublisher);
        verify(aiConversationLock, never()).unlock(any());
    }

    @Test
    void conversationInputLockedBeforeReply_sendMessage_publishesErrorWithoutAiCall() {
        givenNewMessage();
        willDoNothing()
                .willThrow(new BusinessException(AiChatErrorCode.AICHAT_INPUT_LOCKED))
                .given(aiChatRoomService).validateMessageSendableRoom(USER_ID, ROOM_ID);

        aiChatFacade.sendMessage(USER_ID, ROOM_ID, sendMessageRequest());

        verifyNoInteractions(aiChatClient);
        verify(aiReplyJobService).failTakenJob(JOB_ID, ATTEMPT, AiChatErrorCode.AICHAT_INPUT_LOCKED.name());
        verify(aiChatEventPublisher).publish(ROOM_ID, AiChatEventType.ERROR, new AiChatErrorEvent(
                USER_MESSAGE_ID, AiChatErrorCode.AICHAT_INPUT_LOCKED.getMessage(), true, false));
        verify(aiConversationLock).unlock(LOCK_HANDLE);
    }

    @Test
    void jobAlreadySucceeded_sendMessage_republishesSavedReplyWithoutAccepted() {
        givenNewMessage();
        givenTakeResult(finishedTakeResult(AiReplyJobTakeResult.Outcome.ALREADY_SUCCEEDED));
        given(aiMessageService.findReply(USER_MESSAGE_ID))
                .willReturn(Optional.of(message(AI_MESSAGE_ID, USER_MESSAGE_ID)));

        aiChatFacade.sendMessage(USER_ID, ROOM_ID, sendMessageRequest());

        verifyNoInteractions(aiChatClient);
        var order = inOrder(aiConversationLock, aiChatEventPublisher);
        order.verify(aiConversationLock).unlock(LOCK_HANDLE);
        order.verify(aiChatEventPublisher).publish(ROOM_ID, AiChatEventType.COMPLETED,
                new AiChatCompletedEvent(USER_MESSAGE_ID, AI_MESSAGE_ID, AI_CONTENT, 5, false));
        order.verify(aiChatEventPublisher).publish(ROOM_ID, AiChatEventType.DONE, new AiChatDoneEvent(USER_MESSAGE_ID));
        verify(aiChatEventPublisher, never()).publish(eq(ROOM_ID), eq(AiChatEventType.ACCEPTED), any());
    }

    @Test
    void jobGaveUp_sendMessage_publishesRetryableErrorAndReleasesLockWithoutAiCall() {
        givenNewMessage();
        givenTakeResult(finishedTakeResult(AiReplyJobTakeResult.Outcome.GAVE_UP));

        aiChatFacade.sendMessage(USER_ID, ROOM_ID, sendMessageRequest());

        verifyNoInteractions(aiChatClient);
        verify(aiChatEventPublisher).publish(ROOM_ID, AiChatEventType.ERROR, new AiChatErrorEvent(
                USER_MESSAGE_ID, CommonErrorCode.COMMON_SERVICE_UNAVAILABLE.getMessage(), true, false));
        verify(aiChatEventPublisher).publish(ROOM_ID, AiChatEventType.DONE, new AiChatDoneEvent(USER_MESSAGE_ID));
        verify(aiGenerationStateService).finishGenerating(ROOM_ID, USER_MESSAGE_ID);
        verify(aiConversationLock).unlock(LOCK_HANDLE);
    }

    @Test
    void staleJob_sendMessage_doesNothingAfterTake() {
        givenNewMessage();
        givenTakeResult(finishedTakeResult(AiReplyJobTakeResult.Outcome.STALE));

        aiChatFacade.sendMessage(USER_ID, ROOM_ID, sendMessageRequest());

        verifyNoInteractions(aiChatClient, aiChatEventPublisher);
        verify(aiConversationLock, never()).unlock(any());
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
    void confirmInProgress_createAnalysis_throwsAlreadyConfirmedWithoutCallingAiServer() {
        given(aiConversationLock.isLocked(ROOM_ID)).willReturn(true);

        assertThatThrownBy(() -> aiChatFacade.createAnalysis(USER_ID, ROOM_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(AiChatErrorCode.AICHAT_ANALYSIS_ALREADY_CONFIRMED);
        verifyNoInteractions(aiChatClient);
        verify(aiChatRoomService, never()).expireRoom(anyLong());
    }

    // 확정이 세션을 닫고 결과를 저장하는 중이라 대화방을 만료시키면 완료된 대화방을 덮어쓴다
    @Test
    void aiSessionClosedByConfirm_createAnalysis_keepsRoomAndThrowsAlreadyConfirmed() {
        given(aiChatClient.createAnalysis(ROOM_ID))
                .willThrow(new BusinessException(AiChatErrorCode.AICHAT_SESSION_CLOSED));

        assertThatThrownBy(() -> aiChatFacade.createAnalysis(USER_ID, ROOM_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(AiChatErrorCode.AICHAT_ANALYSIS_ALREADY_CONFIRMED);
        verify(aiChatRoomService, never()).expireRoom(anyLong());
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
        given(aiChatClient.confirmAnalysis(USER_ID, ROOM_ID, null)).willReturn(closeResponse);

        ProductRecommendationStatusResponse response = aiChatFacade.confirmAnalysis(USER_ID, ROOM_ID);

        assertThat(response.isRecommendationCompleted()).isTrue();
        var order = inOrder(aiChatRoomService, aiChatClient, aiAnalysisConfirmService);
        order.verify(aiChatRoomService).startAnalysis(ROOM_ID);
        order.verify(aiChatClient).confirmAnalysis(USER_ID, ROOM_ID, null);
        order.verify(aiAnalysisConfirmService).saveConfirmedAnalysis(USER_ID, ROOM_ID, closeResponse);
        verify(aiChatRoomService, never()).expireRoom(anyLong());
    }

    @Test
    void recentActivities_confirmAnalysis_sendsFeedbackToAiServer() {
        LocalDateTime occurredAt = LocalDateTime.of(2026, 10, 30, 12, 0);
        ProductActivityResponse activity = new ProductActivityResponse(
                PlatformType.COUPANG, "8812345", ProductActivityType.DISLIKE, ProductContext.PERSONAL, occurredAt);
        AiServerFeedbackRequest feedback = new AiServerFeedbackRequest(List.of(new AiServerFeedbackItemRequest(
                PlatformType.COUPANG, "8812345", ProductActivityType.DISLIKE, ProductContext.PERSONAL,
                OffsetDateTime.of(occurredAt, ZoneOffset.UTC))));
        AiServerCloseSessionResponse closeResponse = closeSessionResponse(List.of(), List.of("캠핑"));
        given(productActivityService.findAllRecentActivities(USER_ID)).willReturn(List.of(activity));
        given(aiChatClient.confirmAnalysis(USER_ID, ROOM_ID, feedback)).willReturn(closeResponse);

        aiChatFacade.confirmAnalysis(USER_ID, ROOM_ID);

        var order = inOrder(productActivityService, aiChatRoomService, aiChatClient);
        order.verify(productActivityService).findAllRecentActivities(USER_ID);
        order.verify(aiChatRoomService).startAnalysis(ROOM_ID);
        order.verify(aiChatClient).confirmAnalysis(USER_ID, ROOM_ID, feedback);
    }

    @Test
    void feedbackRejected_confirmAnalysis_retriesOnceWithoutFeedbackAndSaves() {
        AiServerCloseSessionResponse closeResponse = closeSessionResponse(List.of(), List.of("캠핑"));
        given(productActivityService.findAllRecentActivities(USER_ID)).willReturn(List.of(dislikeActivity()));
        given(aiChatClient.confirmAnalysis(eq(USER_ID), eq(ROOM_ID), any(AiServerFeedbackRequest.class)))
                .willThrow(new BusinessException(AiChatErrorCode.AICHAT_FEEDBACK_REJECTED));
        given(aiChatClient.confirmAnalysis(USER_ID, ROOM_ID, null)).willReturn(closeResponse);

        ProductRecommendationStatusResponse response = aiChatFacade.confirmAnalysis(USER_ID, ROOM_ID);

        assertThat(response.isRecommendationCompleted()).isTrue();
        verify(aiChatClient).confirmAnalysis(USER_ID, ROOM_ID, null);
        verify(aiAnalysisConfirmService).saveConfirmedAnalysis(USER_ID, ROOM_ID, closeResponse);
        verify(aiChatRoomService, never()).expireRoom(anyLong());
    }

    // 피드백과 상관없는 실패는 피드백을 빼고 보내도 같다 => 다시 닫지 않는다
    @Test
    void aiServerUnavailableWithFeedback_confirmAnalysis_doesNotRetry() {
        given(productActivityService.findAllRecentActivities(USER_ID)).willReturn(List.of(dislikeActivity()));
        given(aiChatClient.confirmAnalysis(eq(USER_ID), eq(ROOM_ID), any(AiServerFeedbackRequest.class)))
                .willThrow(new BusinessException(AiChatErrorCode.AICHAT_SERVER_UNAVAILABLE));

        assertThatThrownBy(() -> aiChatFacade.confirmAnalysis(USER_ID, ROOM_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(AiChatErrorCode.AICHAT_SERVER_UNAVAILABLE);
        verify(aiChatClient, times(1)).confirmAnalysis(any(), any(), any());
        verifyNoInteractions(aiAnalysisConfirmService);
    }

    @Test
    void retryWithoutFeedbackFails_confirmAnalysis_throwsWithoutSaving() {
        given(productActivityService.findAllRecentActivities(USER_ID)).willReturn(List.of(dislikeActivity()));
        given(aiChatClient.confirmAnalysis(eq(USER_ID), eq(ROOM_ID), any(AiServerFeedbackRequest.class)))
                .willThrow(new BusinessException(AiChatErrorCode.AICHAT_FEEDBACK_REJECTED));
        given(aiChatClient.confirmAnalysis(USER_ID, ROOM_ID, null))
                .willThrow(new BusinessException(AiChatErrorCode.AICHAT_REQUEST_REJECTED));

        assertThatThrownBy(() -> aiChatFacade.confirmAnalysis(USER_ID, ROOM_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(AiChatErrorCode.AICHAT_REQUEST_REJECTED);
        verify(aiChatClient, times(2)).confirmAnalysis(any(), any(), any());
        verifyNoInteractions(aiAnalysisConfirmService);
    }

    @Test
    void activityLookupFails_confirmAnalysis_doesNotStartAnalysis() {
        given(productActivityService.findAllRecentActivities(USER_ID))
                .willThrow(new RuntimeException("db down"));

        assertThatThrownBy(() -> aiChatFacade.confirmAnalysis(USER_ID, ROOM_ID))
                .hasMessage("db down");

        verify(aiChatRoomService, never()).startAnalysis(anyLong());
        verifyNoInteractions(aiChatClient);
    }

    @Test
    void finalKeywordCountOverThree_confirmAnalysis_expiresRoomWithoutSavingResult() {
        given(aiChatClient.confirmAnalysis(USER_ID, ROOM_ID, null)).willReturn(closeSessionResponse(
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
        given(aiChatClient.confirmAnalysis(USER_ID, ROOM_ID, null)).willReturn(closeResponse);
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
        given(aiChatClient.confirmAnalysis(USER_ID, ROOM_ID, null)).willReturn(closeResponse);
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
        given(aiChatClient.confirmAnalysis(USER_ID, ROOM_ID, null))
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

    private static ProductActivityResponse dislikeActivity() {
        return new ProductActivityResponse(PlatformType.COUPANG, "8812345", ProductActivityType.DISLIKE,
                ProductContext.PERSONAL, LocalDateTime.of(2026, 10, 30, 12, 0));
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
                new AiServerRecommendationsResponse(self, gift), null);
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

    private AiChatFacade createFacade(AiReplyQueueProperties replyQueueProperties, Set<Long> shortCooldownExternalIds) {
        return new AiChatFacade(aiChatRoomService, aiMessageService, aiReplyJobService, aiConversationLock,
                aiGenerationStateService, aiChatClient, aiAnalysisConfirmService, aiChatStreamService,
                aiChatEventPublisher, aiReplyTaskExecutor, aiReplyQueueService, replyQueueProperties, userService,
                consentService, productActivityService, MESSAGE_RETRY_AFTER, SHORT_COMPLETION_COOLDOWN,
                shortCooldownExternalIds);
    }

    private void givenNewMessage() {
        AiMessage userMessage = message(USER_MESSAGE_ID, null);
        given(aiMessageService.findUserMessage(ROOM_ID, CLIENT_MESSAGE_ID)).willReturn(Optional.empty());
        given(aiReplyJobService.createUserMessageAndJob(ROOM_ID, USER_ID, CLIENT_MESSAGE_ID, USER_CONTENT, LOCK_TOKEN))
                .willReturn(new AiReplyJobDispatch(JOB_ID, DISPATCH_TOKEN, userMessage));
        givenJobTaken(userMessage);
    }

    private void givenJobTaken(AiMessage userMessage) {
        givenTakeResult(new AiReplyJobTakeResult(AiReplyJobTakeResult.Outcome.TAKEN, JOB_ID, ROOM_ID, USER_ID,
                USER_MESSAGE_ID, LOCK_TOKEN, ATTEMPT, userMessage));
    }

    private void givenTakeResult(AiReplyJobTakeResult result) {
        lenient().when(aiReplyJobService.take(JOB_ID, DISPATCH_TOKEN, 1L, null)).thenReturn(result);
    }

    private static AiReplyJobTakeResult finishedTakeResult(AiReplyJobTakeResult.Outcome outcome) {
        return new AiReplyJobTakeResult(outcome, JOB_ID, ROOM_ID, USER_ID, USER_MESSAGE_ID, LOCK_TOKEN, ATTEMPT, null);
    }

    private void verifyInvalidResponseError() {
        verify(aiChatEventPublisher).publish(ROOM_ID, AiChatEventType.ERROR, new AiChatErrorEvent(
                USER_MESSAGE_ID, AiChatErrorCode.AICHAT_INVALID_RESPONSE.getMessage(), true, false));
        verify(aiReplyJobService, never()).commitReply(anyLong(), anyInt(), any(), anyInt(), anyBoolean(), any());
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
