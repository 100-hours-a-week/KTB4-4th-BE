package kr.ktb.zura.needu.friend.controller;

import java.util.List;
import kr.ktb.zura.needu.common.openapi.ControllerDocs;
import kr.ktb.zura.needu.common.openapi.OperationDoc;
import kr.ktb.zura.needu.common.response.RetryAfterResponse;
import kr.ktb.zura.needu.friend.exception.FriendErrorCode;
import kr.ktb.zura.needu.user.exception.UserErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class FriendControllerDocs implements ControllerDocs {

    private static final long EXAMPLE_POKE_RETRY_AFTER_SECONDS = 3540L;

    @Override
    public List<OperationDoc> operations() {
        return List.of(
                OperationDoc.of(FriendController.class, "findAllFriends")
                        .errors(UserErrorCode.USER_NOT_FOUND, UserErrorCode.USER_WITHDRAWN,
                                UserErrorCode.USER_BLOCKED, UserErrorCode.USER_ONBOARDING_REQUIRED)
                        .build(),
                OperationDoc.of(FriendController.class, "findFriend")
                        .errors(UserErrorCode.USER_NOT_FOUND, UserErrorCode.USER_WITHDRAWN,
                                UserErrorCode.USER_BLOCKED, UserErrorCode.USER_ONBOARDING_REQUIRED)
                        .errors(FriendErrorCode.FRIEND_NOT_FOUND)
                        .build(),
                OperationDoc.of(FriendController.class, "updateFavorite")
                        .errors(UserErrorCode.USER_BLOCKED, UserErrorCode.USER_ONBOARDING_REQUIRED)
                        .errors(FriendErrorCode.FRIEND_NOT_FOUND)
                        .build(),
                OperationDoc.of(FriendController.class, "createPoke")
                        .successStatus(HttpStatus.CREATED)
                        .errors(UserErrorCode.USER_BLOCKED, UserErrorCode.USER_ONBOARDING_REQUIRED,
                                FriendErrorCode.FRIEND_NOT_FOUND)
                        .error(FriendErrorCode.FRIEND_POKE_TOO_FREQUENT,
                                new RetryAfterResponse(EXAMPLE_POKE_RETRY_AFTER_SECONDS))
                        .build(),
                OperationDoc.of(FriendController.class, "authorize")
                        .successStatus(HttpStatus.FOUND)
                        .errors(UserErrorCode.USER_BLOCKED, UserErrorCode.USER_ONBOARDING_REQUIRED)
                        .errors(FriendErrorCode.FRIEND_KAKAO_INVALID_RETURN_URL)
                        .build(),
                OperationDoc.of(FriendController.class, "callback")
                        .publicApi()
                        .successStatus(HttpStatus.FOUND)
                        .errors(FriendErrorCode.FRIEND_KAKAO_INVALID_STATE)
                        .build()
        );
    }
}
