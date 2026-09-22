package kr.ktb.zura.needu.user.exception;

import kr.ktb.zura.needu.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements ErrorCode {

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자 정보를 찾을 수 없습니다."),
    USER_KAKAO_ACCOUNT_MISMATCH(HttpStatus.CONFLICT, "로그인한 카카오계정과 일치하지 않습니다."),
    USER_WITHDRAWN(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    USER_BLOCKED(HttpStatus.FORBIDDEN, "이용이 제한된 계정입니다."),
    USER_ONBOARDING_REQUIRED(HttpStatus.FORBIDDEN, "온보딩 진행이 필요합니다.");

    private final HttpStatus status;
    private final String message;
}
