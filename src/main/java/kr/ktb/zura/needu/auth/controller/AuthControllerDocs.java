package kr.ktb.zura.needu.auth.controller;

import java.util.List;
import kr.ktb.zura.needu.auth.exception.AuthErrorCode;
import kr.ktb.zura.needu.common.openapi.ControllerDocs;
import kr.ktb.zura.needu.common.openapi.OperationDoc;
import kr.ktb.zura.needu.user.exception.UserErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class AuthControllerDocs implements ControllerDocs {

    // 로그인 가능한 사용자인지 확인할 때 발생
    private static final List<UserErrorCode> AUTHENTICATED_USER_ERRORS = List.of(
            UserErrorCode.USER_NOT_FOUND,
            UserErrorCode.USER_WITHDRAWN,
            UserErrorCode.USER_BLOCKED);

    @Override
    public List<OperationDoc> operations() {
        return List.of(
                OperationDoc.of(AuthController.class, "authorize")
                        .publicApi()
                        .successStatus(HttpStatus.FOUND)
                        .errors(AuthErrorCode.AUTH_KAKAO_INVALID_RETURN_URL)
                        .build(),
                OperationDoc.of(AuthController.class, "callback")
                        .publicApi()
                        .successStatus(HttpStatus.FOUND)
                        .errors(AuthErrorCode.AUTH_KAKAO_INVALID_STATE, AuthErrorCode.AUTH_KAKAO_CANCELLED,
                                AuthErrorCode.AUTH_KAKAO_INVALID_CODE, AuthErrorCode.AUTH_KAKAO_UNAVAILABLE)
                        .errors(UserErrorCode.USER_WITHDRAWN, UserErrorCode.USER_BLOCKED)
                        .build(),
                OperationDoc.of(AuthController.class, "csrf")
                        .publicApi()
                        .build(),
                OperationDoc.of(AuthController.class, "findSession")
                        .errors(AUTHENTICATED_USER_ERRORS)
                        .build(),
                OperationDoc.of(AuthController.class, "refresh")
                        .publicApi()
                        .errors(AuthErrorCode.AUTH_REFRESH_TOKEN_INVALID)
                        .errors(AUTHENTICATED_USER_ERRORS)
                        .build(),
                OperationDoc.of(AuthController.class, "logout")
                        .publicApi()
                        .successStatus(HttpStatus.NO_CONTENT)
                        .build()
        );
    }
}
