package kr.ktb.zura.needu.common.exception;

import jakarta.validation.ConstraintViolationException;

import kr.ktb.zura.needu.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Object>> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        if (errorCode.getStatus().is5xxServerError()) {
            log.warn("Business exception occurred. code={}", errorCode.name(), e);
        } else {
            log.info("Business exception occurred. code={}", errorCode.name());
        }
        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiResponse.of(errorCode.getMessage(), e.getData()));
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            HttpMediaTypeNotSupportedException.class,
            TypeMismatchException.class,
            ServletRequestBindingException.class,
            MissingServletRequestPartException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleInvalidRequest(Exception e) {
        return toErrorResponse(CommonErrorCode.COMMON_INVALID_REQUEST);
    }

    @ExceptionHandler({
            BindException.class,
            HandlerMethodValidationException.class,
            ConstraintViolationException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleInvalidInput(Exception e) {
        return toErrorResponse(CommonErrorCode.COMMON_INVALID_INPUT);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(NoResourceFoundException e) {
        return toErrorResponse(CommonErrorCode.COMMON_RESOURCE_NOT_FOUND);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return toErrorResponse(CommonErrorCode.COMMON_METHOD_NOT_ALLOWED);
    }

    // 인증-인가 예외를 여기서 응답으로 바꾸면 Spring Security의 EntryPoint/AccessDeniedHandler가 동작하지 않으므로 그대로 전파한다.
    @ExceptionHandler({AuthenticationException.class, AccessDeniedException.class})
    public void rethrowSecurityException(RuntimeException e) {
        throw e;
    }

    // SSE 연결 타임아웃-클라이언트 연결 종료는 서버 결함이 아니고, 이미 text/event-stream 응답이 시작되어 JSON 본문을 쓸 수 없다.
    @ExceptionHandler({AsyncRequestTimeoutException.class, AsyncRequestNotUsableException.class})
    public void handleAsyncRequestTermination(Exception e) {
        log.debug("Async request terminated. reason={}", e.getClass().getSimpleName());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception e) {
        log.error("Unexpected exception occurred", e);
        return toErrorResponse(CommonErrorCode.COMMON_INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<ApiResponse<Void>> toErrorResponse(ErrorCode errorCode) {
        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiResponse.from(errorCode));
    }
}
