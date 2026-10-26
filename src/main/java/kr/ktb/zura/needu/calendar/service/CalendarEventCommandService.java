package kr.ktb.zura.needu.calendar.service;

import kr.ktb.zura.needu.calendar.repository.CalendarEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CalendarEventCommandService {

    private final CalendarEventRepository calendarEventRepository;
}
