package kr.ktb.zura.needu.calendar.exception;

import kr.ktb.zura.needu.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CalendarErrorCode implements ErrorCode {

    // 남의 일정도 존재 여부를 드러내지 않도록 같은 404로 응답
    CALENDAR_EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "캘린더 일정을 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String message;
}
