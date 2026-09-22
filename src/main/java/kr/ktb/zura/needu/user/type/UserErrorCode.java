package kr.ktb.zura.needu.user.type;

import kr.ktb.zura.needu.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements ErrorCode {

    USER_UNAVAILABLE(HttpStatus.FORBIDDEN, "이용할 수 없는 계정입니다.");

    private final HttpStatus status;
    private final String message;
}
