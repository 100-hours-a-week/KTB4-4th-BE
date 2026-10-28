package kr.ktb.zura.needu.anonymouschat.controller;

import java.util.List;
import kr.ktb.zura.needu.anonymouschat.exception.AnonymousChatErrorCode;
import kr.ktb.zura.needu.common.exception.CommonErrorCode;
import kr.ktb.zura.needu.common.openapi.ControllerDocs;
import kr.ktb.zura.needu.common.openapi.OperationDoc;
import kr.ktb.zura.needu.common.response.RetryAfterResponse;
import kr.ktb.zura.needu.friend.exception.FriendErrorCode;
import kr.ktb.zura.needu.user.exception.UserErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class AnonymousChatControllerDocs implements ControllerDocs {

    private static final List<UserErrorCode> ACTIVE_USER_ERRORS = List.of(
            UserErrorCode.USER_BLOCKED,
            UserErrorCode.USER_ONBOARDING_REQUIRED);

    private static final List<AnonymousChatErrorCode> ROOM_ERRORS = List.of(
            AnonymousChatErrorCode.ANONYMOUS_CHAT_ROOM_FORBIDDEN,
            AnonymousChatErrorCode.ANONYMOUS_CHAT_ROOM_NOT_FOUND);

    @Override
    public List<OperationDoc> operations() {
        return List.of(
                OperationDoc.of(AnonymousChatController.class, "findAllRooms")
                        .errors(ACTIVE_USER_ERRORS)
                        .errors(CommonErrorCode.COMMON_INVALID_REQUEST, CommonErrorCode.COMMON_INVALID_INPUT)
                        .error(CommonErrorCode.COMMON_TOO_MANY_REQUESTS, new RetryAfterResponse(10))
                        .build(),
                OperationDoc.of(AnonymousChatController.class, "createRoom")
                        .successStatus(HttpStatus.CREATED)
                        .errors(ACTIVE_USER_ERRORS)
                        .errors(FriendErrorCode.FRIEND_NOT_FOUND)
                        .errors(CommonErrorCode.COMMON_INVALID_REQUEST, CommonErrorCode.COMMON_INVALID_INPUT)
                        .error(CommonErrorCode.COMMON_TOO_MANY_REQUESTS, new RetryAfterResponse(10))
                        .build(),
                OperationDoc.of(AnonymousChatController.class, "findRoom")
                        .errors(ACTIVE_USER_ERRORS)
                        .errors(ROOM_ERRORS)
                        .errors(CommonErrorCode.COMMON_INVALID_REQUEST)
                        .error(CommonErrorCode.COMMON_TOO_MANY_REQUESTS, new RetryAfterResponse(10))
                        .build(),
                OperationDoc.of(AnonymousChatController.class, "findAllMessages")
                        .errors(ROOM_ERRORS)
                        .errors(CommonErrorCode.COMMON_INVALID_REQUEST, CommonErrorCode.COMMON_INVALID_INPUT)
                        .error(CommonErrorCode.COMMON_TOO_MANY_REQUESTS, new RetryAfterResponse(10))
                        .build(),
                OperationDoc.of(AnonymousChatController.class, "readMessages")
                        .successStatus(HttpStatus.NO_CONTENT)
                        .errors(ACTIVE_USER_ERRORS)
                        .errors(ROOM_ERRORS)
                        .errors(CommonErrorCode.COMMON_INVALID_REQUEST, CommonErrorCode.COMMON_INVALID_INPUT)
                        .error(CommonErrorCode.COMMON_TOO_MANY_REQUESTS, new RetryAfterResponse(10))
                        .build(),
                OperationDoc.of(AnonymousChatController.class, "reportChat")
                        .successStatus(HttpStatus.CREATED)
                        .errors(ACTIVE_USER_ERRORS)
                        .errors(ROOM_ERRORS)
                        .errors(AnonymousChatErrorCode.ANONYMOUS_CHAT_REPORT_DUPLICATED,
                                CommonErrorCode.COMMON_INVALID_REQUEST, CommonErrorCode.COMMON_INVALID_INPUT)
                        .error(CommonErrorCode.COMMON_TOO_MANY_REQUESTS, new RetryAfterResponse(10))
                        .build(),
                OperationDoc.of(AnonymousChatController.class, "leaveRoom")
                        .successStatus(HttpStatus.NO_CONTENT)
                        .errors(ACTIVE_USER_ERRORS)
                        .errors(ROOM_ERRORS)
                        .error(CommonErrorCode.COMMON_TOO_MANY_REQUESTS, new RetryAfterResponse(10))
                        .build()
        );
    }
}
