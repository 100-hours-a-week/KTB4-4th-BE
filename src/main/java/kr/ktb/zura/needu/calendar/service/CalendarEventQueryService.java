package kr.ktb.zura.needu.calendar.service;

import kr.ktb.zura.needu.calendar.dto.response.CalendarEventDetailResponse;
import kr.ktb.zura.needu.calendar.exception.CalendarErrorCode;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.calendar.repository.CalendarEventRepository;
import kr.ktb.zura.needu.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CalendarEventQueryService {

    private final CalendarEventRepository calendarEventRepository;
    private final UserService userService;

    public CalendarEventDetailResponse findCalendarEvent(Long userId, Long eventId) {
        userService.validateActiveUser(userId);

        return calendarEventRepository.findCalendarEventDetail(userId, eventId)
                .orElseThrow(() -> new BusinessException(CalendarErrorCode.CALENDAR_EVENT_NOT_FOUND));
    }
}
