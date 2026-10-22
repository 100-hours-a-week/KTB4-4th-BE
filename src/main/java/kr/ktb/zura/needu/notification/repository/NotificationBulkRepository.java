package kr.ktb.zura.needu.notification.repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class NotificationBulkRepository {

    private static final String INSERT_SQL = """
            INSERT INTO notifications (notification_event_id, receiver_user_id, created_at, updated_at, expires_at)
            VALUES (?, ?, ?, ?, ?)
            """;
    private static final String SELECT_IDS_SQL = """
            SELECT id, receiver_user_id FROM notifications WHERE notification_event_id = ?
            """;

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
        Calendar calendar = Calendar.getInstance(jdbcTimeZone);
        Timestamp created = Timestamp.valueOf(createdAt);
        Timestamp expires = Timestamp.valueOf(expiresAt);
        jdbcTemplate.batchUpdate(INSERT_SQL, receiverUserIds, receiverUserIds.size(), (ps, receiverUserId) -> {
            ps.setLong(1, notificationEventId);
            ps.setLong(2, receiverUserId);
            ps.setTimestamp(3, created, calendar);
            ps.setTimestamp(4, created, calendar);
            ps.setTimestamp(5, expires, calendar);
        });
        return jdbcTemplate.query(SELECT_IDS_SQL,
                        (rs, rowNum) -> Map.entry(rs.getLong("receiver_user_id"), rs.getLong("id")),
                        notificationEventId)
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
