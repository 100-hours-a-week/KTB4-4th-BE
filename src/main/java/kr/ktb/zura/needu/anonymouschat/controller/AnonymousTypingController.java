package kr.ktb.zura.needu.anonymouschat.controller;

import io.sentry.Sentry;
import jakarta.validation.Valid;
import java.security.Principal;
import kr.ktb.zura.needu.anonymouschat.dto.request.UpdateAnonymousTypingRequest;
import kr.ktb.zura.needu.anonymouschat.service.AnonymousTypingService;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.CommonErrorCode;
import kr.ktb.zura.needu.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.stereotype.Controller;

// 타이핑만 했는데 오류가 보이지 않도록, 실패해도 ANONYMOUS_CHAT_ERROR를 보내지 않고 이벤트만 버린다.
// 메시지 전송과 오류 처리가 달라 AnonymousChatMessageController와 분리했다.
@Slf4j
@Controller
@RequiredArgsConstructor
public class AnonymousTypingController {

    private final AnonymousTypingService anonymousTypingService;

    @MessageMapping("/anonymous-chats/{roomId}/typing")
    public void updateTyping(
            Principal principal,
            @DestinationVariable Long roomId,
            @Payload @Valid UpdateAnonymousTypingRequest request
    ) {
        anonymousTypingService.updateTyping(Long.valueOf(principal.getName()), roomId, request.isTyping());
    }

    @MessageExceptionHandler(BusinessException.class)
    public void handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        if (errorCode.getStatus().is5xxServerError()) {
            log.warn("Anonymous typing event dropped by business exception. code={}", errorCode.name(), e);
            captureException(e, errorCode);
        } else {
            log.debug("Anonymous typing event dropped by business exception. code={}", errorCode.name());
        }
    }

    @MessageExceptionHandler({
            MethodArgumentNotValidException.class,
            MessageConversionException.class,
            ConversionFailedException.class
    })
    public void handleInvalidRequest(Exception e) {
        log.debug("Anonymous typing event dropped by invalid request. reason={}", e.getClass().getSimpleName());
    }

    @MessageExceptionHandler(Exception.class)
    public void handleUnexpectedException(Exception e) {
        log.error("Anonymous typing unexpected exception occurred", e);
        captureException(e, CommonErrorCode.COMMON_INTERNAL_SERVER_ERROR);
    }

    private void captureException(Exception e, ErrorCode errorCode) {
        Sentry.withScope(scope -> {
            scope.setTag("error.code", errorCode.name());
            Sentry.captureException(e);
        });
    }
}
