package kr.ktb.zura.needu.calendar.controller;

import java.util.List;
import kr.ktb.zura.needu.common.openapi.ControllerDocs;
import kr.ktb.zura.needu.common.openapi.OperationDoc;
import org.springframework.stereotype.Component;

@Component
public class CalendarControllerDocs implements ControllerDocs {

    @Override
    public List<OperationDoc> operations() {
        return List.of();
    }
}
