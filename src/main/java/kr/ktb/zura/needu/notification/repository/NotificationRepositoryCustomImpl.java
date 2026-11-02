package kr.ktb.zura.needu.notification.repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

@RequiredArgsConstructor
public class NotificationRepositoryCustomImpl implements NotificationRepositoryCustom {

    private static final String INSERT_SQL = """
            INSERT INTO notifications (notification_event_id, receiver_user_id, created_at, updated_at, expires_at)
            VALUES (?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;

    @Override
    public List<Long> insertAll(
            Long eventId,
            List<Long> receiverUserIds,
            LocalDateTime createdAt,
            LocalDateTime expiresAt
    ) {
        if (receiverUserIds.isEmpty()) {
            return List.of();
        }

        // rewriteBatchedStatements=true면 드라이버가 다중 행 INSERT 한 문장으로 합쳐 왕복 1회로 보낸다
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.batchUpdate(
                connection -> connection.prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS),
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement statement, int index) throws SQLException {
                        statement.setLong(1, eventId);
                        statement.setLong(2, receiverUserIds.get(index));
                        statement.setObject(3, createdAt);
                        statement.setObject(4, createdAt);
                        statement.setObject(5, expiresAt);
                    }

                    @Override
                    public int getBatchSize() {
                        return receiverUserIds.size();
                    }
                },
                keyHolder
        );

        List<Map<String, Object>> keys = keyHolder.getKeyList();
        if (keys.size() != receiverUserIds.size()) {
            throw new IllegalStateException("Generated notification id count mismatch. expected="
                    + receiverUserIds.size() + ", actual=" + keys.size());
        }
        return keys.stream()
                .map(key -> ((Number) key.values().iterator().next()).longValue())
                .toList();
    }
}
