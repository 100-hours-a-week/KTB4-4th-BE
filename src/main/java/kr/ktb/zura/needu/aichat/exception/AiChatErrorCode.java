package kr.ktb.zura.needu.aichat.exception;

import kr.ktb.zura.needu.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AiChatErrorCode implements ErrorCode {

    // AI 서버 장애는 FE 명세상 500/503만 노출 => 공통 문구로 응답, 원인은 코드 이름으로 구분
    AICHAT_SERVER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "일시적으로 서비스를 이용할 수 없습니다."),
    AICHAT_REQUEST_TIMEOUT(HttpStatus.SERVICE_UNAVAILABLE, "일시적으로 서비스를 이용할 수 없습니다."),
    AICHAT_INVALID_RESPONSE(HttpStatus.SERVICE_UNAVAILABLE, "일시적으로 서비스를 이용할 수 없습니다."),
    AICHAT_AUTHENTICATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "요청을 처리하지 못했습니다."),
    AICHAT_REQUEST_REJECTED(HttpStatus.SERVICE_UNAVAILABLE, "일시적으로 서비스를 이용할 수 없습니다."),
    AICHAT_CONVERSATION_STARTING(HttpStatus.SERVICE_UNAVAILABLE, "일시적으로 서비스를 이용할 수 없습니다.");

    private final HttpStatus status;
    private final String message;
}
