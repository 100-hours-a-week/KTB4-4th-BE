package kr.ktb.zura.needu.common.config;

import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.cloudwatch2.CloudWatchMeterRegistry;
import java.util.Set;
import org.springframework.boot.micrometer.metrics.autoconfigure.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CloudWatchMetricsConfig {

    private static final Set<String> CLOUDWATCH_METRICS = Set.of(
            "hikaricp.connections.active",
            "hikaricp.connections.pending"
    );

    @Bean
    MeterRegistryCustomizer<CloudWatchMeterRegistry> cloudWatchMeterRegistryCustomizer() {
        return registry -> registry.config().meterFilter(cloudWatchMeterFilter());
    }

    MeterFilter cloudWatchMeterFilter() {
        return MeterFilter.denyUnless(id -> CLOUDWATCH_METRICS.contains(id.getName()));
    }
}
