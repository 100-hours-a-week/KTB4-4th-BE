package kr.ktb.zura.needu.common.config;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CloudWatchMetricsConfigTest {

    @Test
    void registerMetrics_allowsOnlyHikariConnectionMetrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        registry.config().meterFilter(new CloudWatchMetricsConfig().cloudWatchMeterFilter());

        registry.gauge("hikaricp.connections.active", new AtomicInteger());
        registry.gauge("hikaricp.connections.pending", new AtomicInteger());
        registry.gauge("jvm.memory.used", new AtomicInteger());

        assertThat(registry.find("hikaricp.connections.active").meter()).isNotNull();
        assertThat(registry.find("hikaricp.connections.pending").meter()).isNotNull();
        assertThat(registry.find("jvm.memory.used").meter()).isNull();
    }
}
