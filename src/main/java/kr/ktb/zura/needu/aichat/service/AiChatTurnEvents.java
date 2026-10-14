package kr.ktb.zura.needu.aichat.service;

import java.util.UUID;

import kr.ktb.zura.needu.aichat.dto.response.AiMessageResponse;
import kr.ktb.zura.needu.aichat.exception.AiChatErrorCode;
import kr.ktb.zura.needu.aichat.sse.AiChatAcceptedEvent;
import kr.ktb.zura.needu.aichat.sse.AiChatDeltaEvent;
import kr.ktb.zura.needu.aichat.sse.AiChatDoneEvent;
import kr.ktb.zura.needu.aichat.sse.AiChatErrorEvent;
import kr.ktb.zura.needu.aichat.sse.AiChatEventType;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.CommonErrorCode;
import kr.ktb.zura.needu.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AiChatTurnEvents {

    private final AiChatEventPublisher publisher;
    private final Long conversationId;
    private Long userMessageId;
    private int nextDeltaIndex;
    private boolean publishFailed;

    public AiChatTurnEvents(AiChatEventPublisher publisher, Long conversationId) {
        this.publisher = publisher;
        this.conversationId = conversationId;
    }

    public void accept(Long userMessageId, UUID clientMessageId) {
        this.userMessageId = userMessageId;
        this.nextDeltaIndex = 0;
        publish(AiChatEventType.ACCEPTED, new AiChatAcceptedEvent(userMessageId, clientMessageId));
    }

    public void publishDelta(String text) {
        publish(AiChatEventType.DELTA, new AiChatDeltaEvent(userMessageId, nextDeltaIndex++, text));
    }

    public void complete(AiMessageResponse response) {
        if (!isAccepted()) {
            return;
        }
        publish(AiChatEventType.COMPLETED, response);
        publish(AiChatEventType.DONE, new AiChatDoneEvent(userMessageId));
    }

    public void fail(RuntimeException cause) {
        if (!isAccepted()) {
            return;
        }
        publish(AiChatEventType.ERROR, toErrorEvent(cause));
        publish(AiChatEventType.DONE, new AiChatDoneEvent(userMessageId));
    }

    private boolean isAccepted() {
        return userMessageId != null;
    }

    private AiChatErrorEvent toErrorEvent(RuntimeException cause) {
        ErrorCode errorCode = cause instanceof BusinessException businessException
                ? businessException.getErrorCode()
                : CommonErrorCode.COMMON_INTERNAL_SERVER_ERROR;
        boolean restartRequired = errorCode instanceof AiChatErrorCode aiChatErrorCode
                && aiChatErrorCode.isSessionGone();
        boolean retryable = !restartRequired && errorCode != AiChatErrorCode.AICHAT_AUTHENTICATION_FAILED;
        return new AiChatErrorEvent(userMessageId, errorCode.getMessage(), retryable, restartRequired);
    }

    private void publish(AiChatEventType type, Object data) {
        if (publishFailed) {
            return;
        }
        try {
            publisher.publish(conversationId, type, data);
        } catch (RuntimeException e) {
            publishFailed = true;
            log.warn("AI chat event publish failed. conversationId={}, userMessageId={}, type={}",
                    conversationId, userMessageId, type, e);
        }
    }
}
