package kr.ktb.zura.needu.notification.repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;

@Repository
public class NotificationBulkRepository {

    private static final String INSERT_SQL = """
            INSERT INTO notifications (notification_event_id, receiver_user_id, created_at, updated_at, expires_at)
            VALUES (?, ?, ?, ?, ?)
            """;
    private static final String SELECT_IDS_SQL = """
            SELECT id, notification_event_id, receiver_user_id FROM notifications WHERE notification_event_id IN (%s)
            """;
    private static final int IN_CLAUSE_SIZE = 1000;

    private final JdbcTemplate jdbcTemplate;
    private final TimeZone jdbcTimeZone;

    public NotificationBulkRepository(
            JdbcTemplate jdbcTemplate,
            @Value("${spring.jpa.properties.hibernate.jdbc.time_zone:}") String jdbcTimeZone
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.jdbcTimeZone = jdbcTimeZone.isBlank() ? TimeZone.getDefault() : TimeZone.getTimeZone(jdbcTimeZone);
    }

    public Map<Long, Long> insertAll(
            Long notificationEventId,
            List<Long> receiverUserIds,
            LocalDateTime createdAt,
            LocalDateTime expiresAt
    ) {
        List<NotificationInsertRow> rows = receiverUserIds.stream()
                .map(receiverUserId -> new NotificationInsertRow(notificationEventId, receiverUserId, expiresAt))
                .toList();
        return insertAll(rows, createdAt).getOrDefault(notificationEventId, Map.of());
    }

    // 여러 이벤트의 알림을 한 번에 넣고, 이벤트 ID별로 수신자 ID → 알림 ID를 돌려준다
    public Map<Long, Map<Long, Long>> insertAll(List<NotificationInsertRow> rows, LocalDateTime createdAt) {
        if (rows.isEmpty()) {
            return Map.of();
        }
        Calendar calendar = Calendar.getInstance(jdbcTimeZone);
        Timestamp created = Timestamp.valueOf(createdAt);
        jdbcTemplate.batchUpdate(INSERT_SQL, rows, rows.size(), (ps, row) -> {
            ps.setLong(1, row.notificationEventId());
            ps.setLong(2, row.receiverUserId());
            ps.setTimestamp(3, created, calendar);
            ps.setTimestamp(4, created, calendar);
            ps.setTimestamp(5, Timestamp.valueOf(row.expiresAt()), calendar);
        });

        List<Long> eventIds = rows.stream()
                .map(NotificationInsertRow::notificationEventId)
                .distinct()
                .toList();
        Map<Long, Map<Long, Long>> idsByEventId = new HashMap<>();
        RowCallbackHandler collectIds = rs -> idsByEventId
                .computeIfAbsent(rs.getLong("notification_event_id"), key -> new HashMap<>())
                .put(rs.getLong("receiver_user_id"), rs.getLong("id"));
        for (int from = 0; from < eventIds.size(); from += IN_CLAUSE_SIZE) {
            List<Long> chunk = eventIds.subList(from, Math.min(from + IN_CLAUSE_SIZE, eventIds.size()));
            String placeholders = String.join(", ", Collections.nCopies(chunk.size(), "?"));
            jdbcTemplate.query(SELECT_IDS_SQL.formatted(placeholders), collectIds, chunk.toArray());
        }
        return idsByEventId;
    }

    public record NotificationInsertRow(Long notificationEventId, Long receiverUserId, LocalDateTime expiresAt) {
    }
}
