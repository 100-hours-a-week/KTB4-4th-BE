package kr.ktb.zura.needu.notification.controller;

import java.util.List;
import kr.ktb.zura.needu.common.exception.CommonErrorCode;
import kr.ktb.zura.needu.common.openapi.ControllerDocs;
import kr.ktb.zura.needu.common.openapi.OperationDoc;
import kr.ktb.zura.needu.common.response.RetryAfterResponse;
import kr.ktb.zura.needu.notification.exception.NotificationErrorCode;
import kr.ktb.zura.needu.user.exception.UserErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class NotificationControllerDocs implements ControllerDocs {

    private static final long EXAMPLE_RETRY_AFTER_SECONDS = 10L;

    @Override
    public List<OperationDoc> operations() {
        return List.of(
                OperationDoc.of(NotificationController.class, "findNotificationSummary")
                        .errors(UserErrorCode.USER_BLOCKED, UserErrorCode.USER_ONBOARDING_REQUIRED)
                        .build(),
                OperationDoc.of(NotificationController.class, "findAllNotifications")
                        .errors(UserErrorCode.USER_BLOCKED, UserErrorCode.USER_ONBOARDING_REQUIRED)
                        .build(),
                OperationDoc.of(NotificationController.class, "readNotification")
                        .errors(NotificationErrorCode.NOTIFICATION_NOT_FOUND)
                        .build(),
                OperationDoc.of(NotificationController.class, "deleteNotification")
                        .successStatus(HttpStatus.NO_CONTENT)
                        .errors(NotificationErrorCode.NOTIFICATION_NOT_FOUND)
                        .build(),
                OperationDoc.of(NotificationController.class, "connectStream")
                        .error(NotificationErrorCode.NOTIFICATION_STREAM_LIMIT_EXCEEDED,
                                new RetryAfterResponse(EXAMPLE_RETRY_AFTER_SECONDS))
                        .errors(CommonErrorCode.COMMON_SERVICE_UNAVAILABLE)
                        .build(),
                OperationDoc.of(NotificationSettingController.class, "findAllNotificationSettings")
                        .errors(UserErrorCode.USER_BLOCKED, UserErrorCode.USER_ONBOARDING_REQUIRED)
                        .build(),
                OperationDoc.of(PushSubscriptionController.class, "unsubscribe")
                        .successStatus(HttpStatus.NO_CONTENT)
                        .build()
        );
    }
}
