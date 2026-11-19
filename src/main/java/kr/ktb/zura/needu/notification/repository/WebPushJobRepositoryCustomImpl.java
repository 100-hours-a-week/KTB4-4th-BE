package kr.ktb.zura.needu.notification.repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import kr.ktb.zura.needu.notification.type.WebPushJobStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;

@RequiredArgsConstructor
public class WebPushJobRepositoryCustomImpl implements WebPushJobRepositoryCustom {

    private static final String INSERT_SQL = """
            INSERT INTO webpush_jobs (notification_event_id, queue_type, notification_ids, status, retry_count,
                                      available_at, created_at, updated_at)
            VALUES (?, ?, ?, ?, 0, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;

    // IDENTITY 키라 JPA saveAll은 행마다 INSERT하므로 JDBC 배치로 보낸다.
    // rewriteBatchedStatements=true면 드라이버가 다중 행 INSERT 한 문장으로 합쳐 왕복 1회로 보낸다
    @Override
    public void insertAll(List<WebPushJobInsertRow> rows, LocalDateTime createdAt) {
        if (rows.isEmpty()) {
            return;
        }

        jdbcTemplate.batchUpdate(INSERT_SQL, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement statement, int index) throws SQLException {
                WebPushJobInsertRow row = rows.get(index);
                statement.setLong(1, row.notificationEventId());
                statement.setString(2, row.queueType().name());
                statement.setString(3, toJsonArray(row.notificationIds()));
                statement.setString(4, WebPushJobStatus.QUEUED.name());
                statement.setObject(5, createdAt);
                statement.setObject(6, createdAt);
                statement.setObject(7, createdAt);
            }

            @Override
            public int getBatchSize() {
                return rows.size();
            }
        });
    }

    // 숫자 배열이라 JSON 라이브러리 없이 만든다
    private static String toJsonArray(List<Long> notificationIds) {
        return notificationIds.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(",", "[", "]"));
    }
}
