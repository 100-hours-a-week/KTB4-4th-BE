package kr.ktb.zura.needu.calendar.service;

import kr.ktb.zura.needu.calendar.repository.CalendarEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CalendarEventQueryService {

    private final CalendarEventRepository calendarEventRepository;
}
