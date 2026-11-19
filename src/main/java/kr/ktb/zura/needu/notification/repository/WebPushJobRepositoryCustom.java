package kr.ktb.zura.needu.notification.repository;

import java.time.LocalDateTime;
import java.util.List;

public interface WebPushJobRepositoryCustom {

    /**
     * 작업을 QUEUED 상태로 일괄 INSERT한다. {@code createdAt}부터 바로 가져갈 수 있다.
     */
    void insertAll(List<WebPushJobInsertRow> rows, LocalDateTime createdAt);
}
