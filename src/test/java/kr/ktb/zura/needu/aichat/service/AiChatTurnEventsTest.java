package kr.ktb.zura.needu.aichat.service;

import java.util.UUID;

import kr.ktb.zura.needu.aichat.exception.AiChatErrorCode;
import kr.ktb.zura.needu.aichat.sse.AiChatAcceptedEvent;
import kr.ktb.zura.needu.aichat.sse.AiChatCompletedEvent;
import kr.ktb.zura.needu.aichat.sse.AiChatDeltaEvent;
import kr.ktb.zura.needu.aichat.sse.AiChatDoneEvent;
import kr.ktb.zura.needu.aichat.sse.AiChatErrorEvent;
import kr.ktb.zura.needu.aichat.sse.AiChatEventType;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.TooManyRequestsException;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class AiChatTurnEventsTest {

    private static final Long CONVERSATION_ID = 101L;
    private static final Long USER_MESSAGE_ID = 201L;
    private static final UUID CLIENT_MESSAGE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final AiChatEventPublisher publisher = mock(AiChatEventPublisher.class);
    private final AiChatTurnEvents turnEvents = new AiChatTurnEvents(publisher, CONVERSATION_ID);

    @Test
    void repliedTurn_complete_publishesAcceptedDeltasCompletedAndDoneInOrder() {
        AiChatCompletedEvent completed = new AiChatCompletedEvent(USER_MESSAGE_ID, 202L, "좋아요", 10, false);

        turnEvents.accept(USER_MESSAGE_ID, CLIENT_MESSAGE_ID);
        turnEvents.publishDelta("좋아요");
        turnEvents.complete(completed);

        InOrder order = inOrder(publisher);
        order.verify(publisher).publish(CONVERSATION_ID, AiChatEventType.ACCEPTED,
                new AiChatAcceptedEvent(USER_MESSAGE_ID, CLIENT_MESSAGE_ID));
        order.verify(publisher).publish(CONVERSATION_ID, AiChatEventType.DELTA,
                new AiChatDeltaEvent(USER_MESSAGE_ID, 0, "좋아요"));
        order.verify(publisher).publish(CONVERSATION_ID, AiChatEventType.COMPLETED, completed);
        order.verify(publisher).publish(CONVERSATION_ID, AiChatEventType.DONE,
                new AiChatDoneEvent(USER_MESSAGE_ID));
    }

    @Test
    void multipleDeltas_publishDelta_publishesWithIncreasingIndexInOrder() {
        turnEvents.accept(USER_MESSAGE_ID, CLIENT_MESSAGE_ID);

        turnEvents.publishDelta("안녕");
        turnEvents.publishDelta("하세");
        turnEvents.publishDelta("요");

        InOrder order = inOrder(publisher);
        order.verify(publisher).publish(CONVERSATION_ID, AiChatEventType.DELTA,
                new AiChatDeltaEvent(USER_MESSAGE_ID, 0, "안녕"));
        order.verify(publisher).publish(CONVERSATION_ID, AiChatEventType.DELTA,
                new AiChatDeltaEvent(USER_MESSAGE_ID, 1, "하세"));
        order.verify(publisher).publish(CONVERSATION_ID, AiChatEventType.DELTA,
                new AiChatDeltaEvent(USER_MESSAGE_ID, 2, "요"));
        verify(publisher, times(3)).publish(eq(CONVERSATION_ID), eq(AiChatEventType.DELTA), any());
    }

    @Test
    void temporaryAiFailure_fail_publishesRetryableErrorAndDone() {
        turnEvents.accept(USER_MESSAGE_ID, CLIENT_MESSAGE_ID);

        turnEvents.fail(new BusinessException(AiChatErrorCode.AICHAT_SERVER_UNAVAILABLE));

        InOrder order = inOrder(publisher);
        order.verify(publisher).publish(CONVERSATION_ID, AiChatEventType.ERROR, new AiChatErrorEvent(
                USER_MESSAGE_ID, AiChatErrorCode.AICHAT_SERVER_UNAVAILABLE.getMessage(), true, false));
        order.verify(publisher).publish(CONVERSATION_ID, AiChatEventType.DONE,
                new AiChatDoneEvent(USER_MESSAGE_ID));
    }

    @Test
    void aiSessionGone_fail_publishesRestartRequiredError() {
        turnEvents.accept(USER_MESSAGE_ID, CLIENT_MESSAGE_ID);

        turnEvents.fail(new BusinessException(AiChatErrorCode.AICHAT_SESSION_NOT_FOUND));

        verify(publisher).publish(CONVERSATION_ID, AiChatEventType.ERROR, new AiChatErrorEvent(
                USER_MESSAGE_ID, AiChatErrorCode.AICHAT_SESSION_NOT_FOUND.getMessage(), false, true));
    }

    @Test
    void aiAuthenticationFailed_fail_publishesNonRetryableError() {
        turnEvents.accept(USER_MESSAGE_ID, CLIENT_MESSAGE_ID);

        turnEvents.fail(new BusinessException(AiChatErrorCode.AICHAT_AUTHENTICATION_FAILED));

        verify(publisher).publish(CONVERSATION_ID, AiChatEventType.ERROR, new AiChatErrorEvent(
                USER_MESSAGE_ID, AiChatErrorCode.AICHAT_AUTHENTICATION_FAILED.getMessage(), false, false));
    }

    @Test
    void aiTurnInProgress_fail_publishesRetryableError() {
        turnEvents.accept(USER_MESSAGE_ID, CLIENT_MESSAGE_ID);

        turnEvents.fail(new TooManyRequestsException(10));

        verify(publisher).publish(CONVERSATION_ID, AiChatEventType.ERROR, new AiChatErrorEvent(
                USER_MESSAGE_ID, "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.", true, false));
    }

    @Test
    void unexpectedFailure_fail_publishesRetryableInternalError() {
        turnEvents.accept(USER_MESSAGE_ID, CLIENT_MESSAGE_ID);

        turnEvents.fail(new IllegalStateException("db down"));

        verify(publisher).publish(CONVERSATION_ID, AiChatEventType.ERROR, new AiChatErrorEvent(
                USER_MESSAGE_ID, "요청을 처리하지 못했습니다.", true, false));
    }

    @Test
    void notAccepted_completeOrFail_publishesNothing() {
        turnEvents.complete(new AiChatCompletedEvent(USER_MESSAGE_ID, 202L, "좋아요", 10, false));
        turnEvents.fail(new BusinessException(AiChatErrorCode.AICHAT_SERVER_UNAVAILABLE));

        verifyNoInteractions(publisher);
    }

    @Test
    void publishFailsMidReply_publishDelta_skipsRemainingEventsWithoutThrowing() {
        doThrow(new IllegalStateException("redis down"))
                .when(publisher).publish(eq(CONVERSATION_ID), eq(AiChatEventType.DELTA), any());
        turnEvents.accept(USER_MESSAGE_ID, CLIENT_MESSAGE_ID);

        turnEvents.publishDelta("가".repeat(20));
        turnEvents.publishDelta("가".repeat(20));
        turnEvents.publishDelta("가".repeat(5));
        turnEvents.complete(new AiChatCompletedEvent(USER_MESSAGE_ID, 202L, "가".repeat(45), 10, false));

        verify(publisher, times(1)).publish(eq(CONVERSATION_ID), eq(AiChatEventType.DELTA), any());
        verify(publisher, times(0)).publish(eq(CONVERSATION_ID), eq(AiChatEventType.COMPLETED), any());
        verify(publisher, times(0)).publish(eq(CONVERSATION_ID), eq(AiChatEventType.DONE), any());
    }
}
