package kr.ktb.zura.needu.calendar.controller;

import java.util.List;
import kr.ktb.zura.needu.common.openapi.ControllerDocs;
import kr.ktb.zura.needu.common.openapi.OperationDoc;
import kr.ktb.zura.needu.user.exception.UserErrorCode;
import org.springframework.stereotype.Component;

@Component
public class CalendarControllerDocs implements ControllerDocs {

    @Override
    public List<OperationDoc> operations() {
        return List.of(
                OperationDoc.of(CalendarController.class, "findMonthlyCalendarEventSummary")
                        .errors(UserErrorCode.USER_BLOCKED, UserErrorCode.USER_ONBOARDING_REQUIRED,
                                UserErrorCode.USER_NOT_FOUND, UserErrorCode.USER_WITHDRAWN)
                        .build()
        );
    }
}
