package kr.ktb.zura.needu.user.controller;

import java.util.List;
import kr.ktb.zura.needu.common.exception.CommonErrorCode;
import kr.ktb.zura.needu.common.openapi.ControllerDocs;
import kr.ktb.zura.needu.common.openapi.OperationDoc;
import kr.ktb.zura.needu.user.exception.UserErrorCode;
import org.springframework.stereotype.Component;

@Component
public class UserControllerDocs implements ControllerDocs {

    @Override
    public List<OperationDoc> operations() {
        return List.of(
                OperationDoc.of(UserController.class, "findMyPage")
                        .errors(UserErrorCode.USER_BLOCKED, UserErrorCode.USER_ONBOARDING_REQUIRED,
                                UserErrorCode.USER_NOT_FOUND)
                        .build(),
                OperationDoc.of(UserController.class, "withdraw")
                        .errors(UserErrorCode.USER_BLOCKED, UserErrorCode.USER_WITHDRAWN,
                                UserErrorCode.USER_WITHDRAWAL_IN_PROGRESS, CommonErrorCode.COMMON_SERVICE_UNAVAILABLE)
                        .build(),
                OperationDoc.of(GiftPreferenceController.class, "findGiftPreference")
                        .errors(UserErrorCode.USER_BLOCKED, UserErrorCode.USER_ONBOARDING_REQUIRED,
                                UserErrorCode.USER_NOT_FOUND)
                        .build(),
                OperationDoc.of(GiftPreferenceController.class, "updateGiftPreference")
                        .errors(UserErrorCode.USER_BLOCKED, UserErrorCode.USER_ONBOARDING_REQUIRED,
                                UserErrorCode.USER_NOT_FOUND)
                        .build(),
                OperationDoc.of(OnboardingController.class, "findOnboardingStatus")
                        .errors(UserErrorCode.USER_BLOCKED, UserErrorCode.USER_NOT_FOUND)
                        .build(),
                OperationDoc.of(OnboardingController.class, "completeOnboarding")
                        .errors(UserErrorCode.USER_REQUIRED_CONSENT_MISSING, UserErrorCode.USER_BLOCKED,
                                UserErrorCode.USER_NOT_FOUND, UserErrorCode.USER_ONBOARDING_ALREADY_COMPLETED)
                        .build(),
                OperationDoc.of(ConsentController.class, "findConsents")
                        .errors(UserErrorCode.USER_BLOCKED, UserErrorCode.USER_NOT_FOUND)
                        .build(),
                OperationDoc.of(ConsentController.class, "updateConsents")
                        .errors(UserErrorCode.USER_BLOCKED, UserErrorCode.USER_NOT_FOUND)
                        .build()
        );
    }
}
