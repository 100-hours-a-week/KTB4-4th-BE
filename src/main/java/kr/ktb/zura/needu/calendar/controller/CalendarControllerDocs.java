package kr.ktb.zura.needu.calendar.controller;

import java.util.List;
import kr.ktb.zura.needu.calendar.exception.CalendarErrorCode;
import kr.ktb.zura.needu.common.openapi.ControllerDocs;
import kr.ktb.zura.needu.common.openapi.OperationDoc;
import kr.ktb.zura.needu.user.exception.UserErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class CalendarControllerDocs implements ControllerDocs {

    @Override
    public List<OperationDoc> operations() {
        return List.of(
                OperationDoc.of(CalendarController.class, "createCalendarEvent")
                        .successStatus(HttpStatus.CREATED)
                        .errors(UserErrorCode.USER_BLOCKED, UserErrorCode.USER_ONBOARDING_REQUIRED)
                        .build(),
                OperationDoc.of(CalendarController.class, "updateCalendarEvent")
                        .errors(UserErrorCode.USER_BLOCKED, UserErrorCode.USER_ONBOARDING_REQUIRED)
                        .errors(CalendarErrorCode.CALENDAR_EVENT_NOT_FOUND)
                        .build(),
                OperationDoc.of(CalendarController.class, "deleteCalendarEvent")
                        .successStatus(HttpStatus.NO_CONTENT)
                        .errors(UserErrorCode.USER_BLOCKED, UserErrorCode.USER_ONBOARDING_REQUIRED)
                        .errors(CalendarErrorCode.CALENDAR_EVENT_NOT_FOUND)
                        .build(),
                OperationDoc.of(CalendarController.class, "findMonthlyCalendarEventSummary")
                        .errors(UserErrorCode.USER_BLOCKED, UserErrorCode.USER_ONBOARDING_REQUIRED,
                                UserErrorCode.USER_NOT_FOUND, UserErrorCode.USER_WITHDRAWN)
                        .build(),
                OperationDoc.of(CalendarController.class, "findAllDailyCalendarEvents")
                        .errors(UserErrorCode.USER_BLOCKED, UserErrorCode.USER_ONBOARDING_REQUIRED,
                                UserErrorCode.USER_NOT_FOUND, UserErrorCode.USER_WITHDRAWN)
                        .build(),
                OperationDoc.of(CalendarController.class, "findCalendarEvent")
                        .errors(CalendarErrorCode.CALENDAR_EVENT_NOT_FOUND,
                                UserErrorCode.USER_BLOCKED, UserErrorCode.USER_ONBOARDING_REQUIRED,
                                UserErrorCode.USER_NOT_FOUND, UserErrorCode.USER_WITHDRAWN)
                        .build()
        );
    }
}
