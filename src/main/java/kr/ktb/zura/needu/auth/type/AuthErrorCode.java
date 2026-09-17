package kr.ktb.zura.needu.auth.type;

import kr.ktb.zura.needu.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements ErrorCode {

    AUTH_KAKAO_INVALID_STATE(HttpStatus.BAD_REQUEST, "카카오 로그인 요청이 유효하지 않습니다."),
    AUTH_KAKAO_CANCELLED(HttpStatus.BAD_REQUEST, "카카오 로그인이 취소되었습니다."),
    AUTH_KAKAO_INVALID_CODE(HttpStatus.BAD_REQUEST, "카카오 인가 코드가 유효하지 않습니다."),
    AUTH_KAKAO_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "카카오 로그인을 처리할 수 없습니다.");

    private final HttpStatus status;
    private final String message;
}
