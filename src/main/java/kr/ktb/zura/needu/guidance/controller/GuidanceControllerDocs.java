package kr.ktb.zura.needu.guidance.controller;

import java.util.List;
import kr.ktb.zura.needu.common.openapi.ControllerDocs;
import kr.ktb.zura.needu.common.openapi.OperationDoc;
import kr.ktb.zura.needu.user.exception.UserErrorCode;
import org.springframework.stereotype.Component;

@Component
public class GuidanceControllerDocs implements ControllerDocs {

    @Override
    public List<OperationDoc> operations() {
        return List.of(
                OperationDoc.of(GuidanceController.class, "findGuidance")
                        .errors(UserErrorCode.USER_NOT_FOUND, UserErrorCode.USER_WITHDRAWN,
                                UserErrorCode.USER_BLOCKED, UserErrorCode.USER_ONBOARDING_REQUIRED)
                        .build()
        );
    }
}
