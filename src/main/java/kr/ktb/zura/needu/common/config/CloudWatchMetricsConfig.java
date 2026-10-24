package kr.ktb.zura.needu.common.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micrometer.cloudwatch2.CloudWatchMeterRegistry;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.MeterFilter;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.micrometer.metrics.autoconfigure.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Configuration
public class CloudWatchMetricsConfig {

    private static final Set<String> CLOUDWATCH_METRICS = Set.of(
            "hikaricp.connections.active",
            "hikaricp.connections.pending",
            "process.cpu.usage",
            "system.cpu.usage",
            "jvm.gc.pause",
            "jvm.gc.overhead",
            "jvm.memory.usage.after.gc",
            "jvm.threads.live"
    );
    private static final String JVM_MEMORY_USED = "jvm.memory.used";
    private static final String MEMORY_AREA_TAG = "area";
    private static final String HEAP_AREA = "heap";
    // GC 원인마다 CloudWatch 지표가 따로 생겨 비용이 늘어나므로 원인 구분은 버린다
    private static final String GC_CAUSE_TAG = "cause";

    private static final String TASK_ID_TAG = "taskId";
    private static final String ECS_METADATA_URI_ENV = "ECS_CONTAINER_METADATA_URI_V4";
    private static final String ECS_TASK_METADATA_PATH = "/task";
    private static final Duration ECS_METADATA_TIMEOUT = Duration.ofSeconds(2);
    private static final String UNKNOWN_TASK_ID = "unknown";

    @Bean
    MeterRegistryCustomizer<CloudWatchMeterRegistry> cloudWatchMeterRegistryCustomizer() {
        return registry -> registry.config()
                .commonTags(TASK_ID_TAG, resolveTaskId(ecsMetadataClient(), System.getenv(ECS_METADATA_URI_ENV)))
                .meterFilter(MeterFilter.ignoreTags(GC_CAUSE_TAG))
                .meterFilter(cloudWatchMeterFilter());
    }

    MeterFilter cloudWatchMeterFilter() {
        return MeterFilter.denyUnless(id -> CLOUDWATCH_METRICS.contains(id.getName()) || isHeapMemoryUsed(id));
    }

    // 같은 서비스의 Task들이 한 지표로 합쳐지지 않도록 ECS Task ID로 구분하고, ECS 밖에서는 호스트 이름을 쓴다
    String resolveTaskId(RestClient restClient, String metadataUri) {
        if (metadataUri == null || metadataUri.isBlank()) {
            return findHostName();
        }
        try {
            EcsTaskMetadata metadata = restClient.get()
                    .uri(metadataUri + ECS_TASK_METADATA_PATH)
                    .retrieve()
                    .body(EcsTaskMetadata.class);
            if (metadata == null || metadata.taskArn() == null || metadata.taskArn().isBlank()) {
                log.warn("ECS task metadata has no task ARN. Falling back to host name.");
                return findHostName();
            }
            return toTaskId(metadata.taskArn());
        } catch (RestClientException exception) {
            log.warn("ECS task metadata unavailable. Falling back to host name.", exception);
            return findHostName();
        }
    }

    private boolean isHeapMemoryUsed(Meter.Id id) {
        return JVM_MEMORY_USED.equals(id.getName()) && HEAP_AREA.equals(id.getTag(MEMORY_AREA_TAG));
    }

    private RestClient ecsMetadataClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(ECS_METADATA_TIMEOUT);
        requestFactory.setReadTimeout(ECS_METADATA_TIMEOUT);
        return RestClient.builder().requestFactory(requestFactory).build();
    }

    // Task ARN 형식: arn:aws:ecs:{region}:{account}:task/{cluster}/{taskId}
    private String toTaskId(String taskArn) {
        return taskArn.substring(taskArn.lastIndexOf('/') + 1);
    }

    private String findHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException exception) {
            log.warn("Host name unavailable for CloudWatch task tag.", exception);
            return UNKNOWN_TASK_ID;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record EcsTaskMetadata(@JsonProperty("TaskARN") String taskArn) {
    }
}
