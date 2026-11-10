package kr.ktb.zura.needu.anonymouschat.controller;

import io.sentry.Sentry;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.Map;
import kr.ktb.zura.needu.anonymouschat.dto.request.SendAnonymousMessageRequest;
import kr.ktb.zura.needu.anonymouschat.service.AnonymousMessageService;
import kr.ktb.zura.needu.anonymouschat.ws.AnonymousChatErrorEvent;
import kr.ktb.zura.needu.anonymouschat.ws.AnonymousChatEventSender;
import kr.ktb.zura.needu.anonymouschat.ws.AnonymousMessageFailedEvent;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.CommonErrorCode;
import kr.ktb.zura.needu.common.exception.ErrorCode;
import kr.ktb.zura.needu.common.response.RetryAfterResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.messaging.Message;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.support.DestinationVariableMethodArgumentResolver;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Controller
@RequiredArgsConstructor
public class AnonymousChatMessageController {

    private static final String ROOM_ID_VARIABLE = "roomId";

    private final AnonymousMessageService anonymousMessageService;
    private final JsonMapper jsonMapper;

    @MessageMapping("/anonymous-chats/{roomId}/messages")
    public void sendMessage(
            Principal principal,
            @DestinationVariable Long roomId,
            @Payload @Valid SendAnonymousMessageRequest request
    ) {
        anonymousMessageService.sendMessage(Long.valueOf(principal.getName()), roomId, request);
    }

    @MessageExceptionHandler(BusinessException.class)
    @SendToUser(destinations = AnonymousChatEventSender.USER_DESTINATION, broadcast = false)
    public AnonymousChatErrorEvent handleBusinessException(BusinessException e, Message<?> message) {
        ErrorCode errorCode = e.getErrorCode();
        if (errorCode.getStatus().is5xxServerError()) {
            log.warn("Anonymous chat WebSocket business exception occurred. code={}", errorCode.name(), e);
            captureException(e, errorCode);
        } else {
            log.info("Anonymous chat WebSocket business exception occurred. code={}", errorCode.name());
        }
        Long retryAfterSeconds = e.getData() instanceof RetryAfterResponse retryAfter
                ? retryAfter.retryAfterSeconds()
                : null;
        return AnonymousChatErrorEvent.of(errorCode, toFailedEvent(message, retryAfterSeconds));
    }

    @MessageExceptionHandler(MethodArgumentNotValidException.class)
    @SendToUser(destinations = AnonymousChatEventSender.USER_DESTINATION, broadcast = false)
    public AnonymousChatErrorEvent handleInvalidInput(Message<?> message) {
        return AnonymousChatErrorEvent.of(CommonErrorCode.COMMON_INVALID_INPUT, toFailedEvent(message, null));
    }

    @MessageExceptionHandler({MessageConversionException.class, ConversionFailedException.class})
    @SendToUser(destinations = AnonymousChatEventSender.USER_DESTINATION, broadcast = false)
    public AnonymousChatErrorEvent handleInvalidRequest(Message<?> message) {
        return AnonymousChatErrorEvent.of(CommonErrorCode.COMMON_INVALID_REQUEST, toFailedEvent(message, null));
    }

    @MessageExceptionHandler(Exception.class)
    @SendToUser(destinations = AnonymousChatEventSender.USER_DESTINATION, broadcast = false)
    public AnonymousChatErrorEvent handleUnexpectedException(Exception e, Message<?> message) {
        log.error("Anonymous chat WebSocket unexpected exception occurred", e);
        captureException(e, CommonErrorCode.COMMON_INTERNAL_SERVER_ERROR);
        return AnonymousChatErrorEvent.of(
                CommonErrorCode.COMMON_INTERNAL_SERVER_ERROR, toFailedEvent(message, null));
    }

    private AnonymousMessageFailedEvent toFailedEvent(Message<?> message, Long retryAfterSeconds) {
        return new AnonymousMessageFailedEvent(findRoomId(message), findClientMessageId(message), retryAfterSeconds);
    }

    private Long findRoomId(Message<?> message) {
        if (!(message.getHeaders().get(DestinationVariableMethodArgumentResolver.DESTINATION_TEMPLATE_VARIABLES_HEADER)
                instanceof Map<?, ?> variables)
                || !(variables.get(ROOM_ID_VARIABLE) instanceof String roomId)) {
            return null;
        }
        try {
            return Long.valueOf(roomId);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String findClientMessageId(Message<?> message) {
        if (!(message.getPayload() instanceof byte[] payload)) {
            return null;
        }
        try {
            JsonNode clientMessageId = jsonMapper.readTree(payload).get("clientMessageId");
            return clientMessageId != null && clientMessageId.isString() ? clientMessageId.asString() : null;
        } catch (JacksonException e) {
            return null;
        }
    }

    private void captureException(Exception e, ErrorCode errorCode) {
        Sentry.withScope(scope -> {
            scope.setTag("error.code", errorCode.name());
            Sentry.captureException(e);
        });
    }
}
