package kr.ktb.zura.needu.aichat.exception;

import kr.ktb.zura.needu.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AiChatErrorCode implements ErrorCode {

    AICHAT_SERVER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "AI 서버에 연결할 수 없습니다."),
    AICHAT_REQUEST_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "AI 서버의 응답 시간이 초과되었습니다."),
    AICHAT_INVALID_RESPONSE(HttpStatus.BAD_GATEWAY, "AI 서버로부터 유효하지 않은 응답을 받았습니다."),
    AICHAT_AUTHENTICATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "AI 서버 인증에 실패했습니다."),
    AICHAT_REQUEST_REJECTED(HttpStatus.BAD_GATEWAY, "AI 서버가 요청을 거절했습니다.");

    private final HttpStatus status;
    private final String message;
}
