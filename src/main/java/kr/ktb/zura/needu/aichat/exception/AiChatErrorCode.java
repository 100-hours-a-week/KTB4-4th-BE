package kr.ktb.zura.needu.aichat.exception;

import kr.ktb.zura.needu.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AiChatErrorCode implements ErrorCode {

    AICHAT_CONVERSATION_NOT_FOUND(HttpStatus.NOT_FOUND, "AI 대화를 찾을 수 없습니다."),
    AICHAT_CONVERSATION_FORBIDDEN(HttpStatus.FORBIDDEN, "해당 AI 대화에 접근할 수 없습니다."),

    // AI 서버 세션이 만료 규칙보다 일찍 사라졌거나 이미 닫힌 경우 => FE에는 대화가 없는 것과 같게 보인다
    AICHAT_SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "AI 대화를 찾을 수 없습니다."),
    AICHAT_SESSION_CLOSED(HttpStatus.NOT_FOUND, "AI 대화를 찾을 수 없습니다."),

    // AI 서버가 같은 대화방의 이전 메시지를 아직 처리 중 => 대화방은 그대로 두고 재시도를 안내한다
    AICHAT_TURN_IN_PROGRESS(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."),

    // AI 서버 장애는 FE 명세상 500/503만 노출 => 공통 문구로 응답, 원인은 코드 이름으로 구분
    AICHAT_SERVER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "일시적으로 서비스를 이용할 수 없습니다."),
    AICHAT_REQUEST_TIMEOUT(HttpStatus.SERVICE_UNAVAILABLE, "일시적으로 서비스를 이용할 수 없습니다."),
    AICHAT_INVALID_RESPONSE(HttpStatus.SERVICE_UNAVAILABLE, "일시적으로 서비스를 이용할 수 없습니다."),
    AICHAT_AUTHENTICATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "요청을 처리하지 못했습니다."),
    AICHAT_REQUEST_REJECTED(HttpStatus.SERVICE_UNAVAILABLE, "일시적으로 서비스를 이용할 수 없습니다."),
    AICHAT_CONVERSATION_STARTING(HttpStatus.SERVICE_UNAVAILABLE, "일시적으로 서비스를 이용할 수 없습니다.");

    private final HttpStatus status;
    private final String message;

    // AI 세션이 사라져 대화방을 정리해야 하는 오류인지
    public boolean isSessionGone() {
        return this == AICHAT_SESSION_NOT_FOUND || this == AICHAT_SESSION_CLOSED;
    }
}
