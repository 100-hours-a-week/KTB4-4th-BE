package kr.ktb.zura.needu.common.config;

import io.micrometer.cloudwatch2.CloudWatchConfig;
import io.micrometer.cloudwatch2.CloudWatchMeterRegistry;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.InetAddress;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient;
import software.amazon.awssdk.services.cloudwatch.model.Dimension;
import software.amazon.awssdk.services.cloudwatch.model.MetricDatum;
import software.amazon.awssdk.services.cloudwatch.model.PutMetricDataRequest;
import software.amazon.awssdk.services.cloudwatch.model.PutMetricDataResponse;
import software.amazon.awssdk.services.cloudwatch.model.StandardUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CloudWatchMetricsConfigTest {

    private static final String METADATA_URI = "http://169.254.170.2/v4/container";
    private static final Duration CLOUDWATCH_STEP = Duration.ofMinutes(1);

    private final CloudWatchMetricsConfig config = new CloudWatchMetricsConfig();

    @Test
    void registerMetrics_allowsOnlyHikariCpuAndGcMetrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        registry.config().meterFilter(config.cloudWatchMeterFilter());

        registry.gauge("hikaricp.connections.active", new AtomicInteger());
        registry.gauge("hikaricp.connections.pending", new AtomicInteger());
        registry.gauge("process.cpu.usage", new AtomicInteger());
        registry.gauge("system.cpu.usage", new AtomicInteger());
        registry.timer("jvm.gc.pause");
        registry.gauge("jvm.gc.overhead", new AtomicInteger());
        registry.gauge("jvm.memory.usage.after.gc", new AtomicInteger());
        registry.gauge("jvm.threads.live", new AtomicInteger());
        registry.gauge("jvm.classes.loaded", new AtomicInteger());
        registry.gauge("http.server.requests.active", new AtomicInteger());

        assertThat(registry.find("hikaricp.connections.active").meter()).isNotNull();
        assertThat(registry.find("hikaricp.connections.pending").meter()).isNotNull();
        assertThat(registry.find("process.cpu.usage").meter()).isNotNull();
        assertThat(registry.find("system.cpu.usage").meter()).isNotNull();
        assertThat(registry.find("jvm.gc.pause").meter()).isNotNull();
        assertThat(registry.find("jvm.gc.overhead").meter()).isNotNull();
        assertThat(registry.find("jvm.memory.usage.after.gc").meter()).isNotNull();
        assertThat(registry.find("jvm.threads.live").meter()).isNotNull();
        assertThat(registry.find("jvm.classes.loaded").meter()).isNull();
        assertThat(registry.find("http.server.requests.active").meter()).isNull();
    }

    @Test
    void gcPauses_publishCountAndSumPerTaskWithoutCause() {
        CloudWatchAsyncClient client = mock(CloudWatchAsyncClient.class);
        when(client.putMetricData(any(PutMetricDataRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(PutMetricDataResponse.builder().build()));
        MockClock clock = new MockClock();
        CloudWatchMeterRegistry registry = new CloudWatchMeterRegistry(cloudWatchConfig(), clock, client);
        config.cloudWatchMeterRegistryCustomizer().customize(registry);

        Timer.builder("jvm.gc.pause")
                .tags("gc", "G1 Young Generation", "action", "end of minor GC", "cause", "G1 Evacuation Pause")
                .register(registry)
                .record(30, TimeUnit.MILLISECONDS);
        Timer.builder("jvm.gc.pause")
                .tags("gc", "G1 Young Generation", "action", "end of minor GC", "cause", "G1 Humongous Allocation")
                .register(registry)
                .record(20, TimeUnit.MILLISECONDS);
        clock.add(CLOUDWATCH_STEP.plusMillis(1));
        registry.close();

        List<MetricDatum> metricData = capturePublishedMetricData(client);
        assertThat(findMetricDatum(metricData, "jvm.gc.pause.count").value()).isEqualTo(2.0);
        MetricDatum pauseSum = findMetricDatum(metricData, "jvm.gc.pause.sum");
        assertThat(pauseSum.value()).isEqualTo(50.0);
        assertThat(pauseSum.unit()).isEqualTo(StandardUnit.MILLISECONDS);
        assertThat(pauseSum.dimensions()).extracting(Dimension::name)
                .contains("taskId", "gc", "action")
                .doesNotContain("cause");
    }

    private CloudWatchConfig cloudWatchConfig() {
        return key -> switch (key) {
            case "cloudwatch.namespace" -> "NeedU/Test";
            case "cloudwatch.step" -> CLOUDWATCH_STEP.toString();
            default -> null;
        };
    }

    private List<MetricDatum> capturePublishedMetricData(CloudWatchAsyncClient client) {
        ArgumentCaptor<PutMetricDataRequest> captor = ArgumentCaptor.forClass(PutMetricDataRequest.class);
        verify(client, atLeastOnce()).putMetricData(captor.capture());
        return captor.getAllValues().stream()
                .flatMap(request -> request.metricData().stream())
                .toList();
    }

    private MetricDatum findMetricDatum(List<MetricDatum> metricData, String metricName) {
        return metricData.stream()
                .filter(datum -> datum.metricName().equals(metricName))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void jvmMemoryUsed_allowsOnlyHeapArea() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        registry.config().meterFilter(config.cloudWatchMeterFilter());

        registry.gauge("jvm.memory.used", Tags.of("area", "heap", "id", "G1 Old Gen"), new AtomicInteger());
        registry.gauge("jvm.memory.used", Tags.of("area", "nonheap", "id", "Metaspace"), new AtomicInteger());

        assertThat(registry.find("jvm.memory.used").tag("area", "heap").meter()).isNotNull();
        assertThat(registry.find("jvm.memory.used").tag("area", "nonheap").meter()).isNull();
    }

    @Test
    void ecsTaskMetadata_returnsTaskIdFromTaskArn() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(METADATA_URI + "/task"))
                .andRespond(withSuccess("""
                        {
                          "Cluster": "needu-staging-cluster",
                          "TaskARN": "arn:aws:ecs:ap-northeast-2:123456789012:task/needu-staging-cluster/0a1b2c3d4e5f",
                          "Family": "needu-staging-backend"
                        }
                        """, MediaType.APPLICATION_JSON));

        String taskId = config.resolveTaskId(builder.build(), METADATA_URI);

        assertThat(taskId).isEqualTo("0a1b2c3d4e5f");
        server.verify();
    }

    @Test
    void ecsTaskMetadataFailure_returnsHostName() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(METADATA_URI + "/task"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        String taskId = config.resolveTaskId(builder.build(), METADATA_URI);

        assertThat(taskId).isEqualTo(InetAddress.getLocalHost().getHostName());
    }

    @Test
    void missingEcsMetadataUri_returnsHostName() throws Exception {
        String taskId = config.resolveTaskId(RestClient.create(), null);

        assertThat(taskId).isEqualTo(InetAddress.getLocalHost().getHostName());
    }
}
