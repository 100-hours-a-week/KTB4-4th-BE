package kr.ktb.zura.needu.common.config;

import java.time.Duration;
import java.time.Instant;
import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;

class SchedulingConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SchedulingConfig.class)
            .withBean(DataSource.class, () -> new DriverManagerDataSource(
                    "jdbc:h2:mem:scheduling;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));

    @Test
    void sameLockName_allowsOnlyOneAcquisition() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(LockProvider.class);

            DataSource dataSource = context.getBean(DataSource.class);
            new JdbcTemplate(dataSource).execute("""
                    CREATE TABLE shedlock (
                        name VARCHAR(64) NOT NULL PRIMARY KEY,
                        lock_until DATETIME(6) NOT NULL,
                        locked_at DATETIME(6) NOT NULL,
                        locked_by VARCHAR(255) NOT NULL
                    )
                    """);

            LockProvider lockProvider = context.getBean(LockProvider.class);
            LockConfiguration lockConfiguration = new LockConfiguration(
                    Instant.now(), "testScheduler", Duration.ofMinutes(1), Duration.ZERO);

            SimpleLock acquiredLock = lockProvider.lock(lockConfiguration).orElseThrow();

            assertThat(lockProvider.lock(lockConfiguration)).isEmpty();

            acquiredLock.unlock();
        });
    }
}
