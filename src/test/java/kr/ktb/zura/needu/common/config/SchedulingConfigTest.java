package kr.ktb.zura.needu.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest
@Sql(statements = "CREATE TABLE IF NOT EXISTS shedlock ("
        + "name VARCHAR(64) NOT NULL PRIMARY KEY, "
        + "lock_until DATETIME(6) NOT NULL, "
        + "locked_at DATETIME(6) NOT NULL, "
        + "locked_by VARCHAR(255) NOT NULL)")
class SchedulingConfigTest {

    @Autowired
    private LockProvider lockProvider;

    @Test
    void sameLockName_lockTwice_allowsOnlyFirstAcquisition() {
        LockConfiguration configuration = new LockConfiguration(
                Instant.now(),
                "friendBirthdayNotificationTest",
                Duration.ofMinutes(1),
                Duration.ZERO
        );

        SimpleLock lock = lockProvider.lock(configuration).orElseThrow();

        assertThat(lockProvider.lock(configuration)).isEmpty();
        lock.unlock();
    }
}
