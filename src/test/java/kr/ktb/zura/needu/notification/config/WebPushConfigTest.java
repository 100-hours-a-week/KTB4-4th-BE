package kr.ktb.zura.needu.notification.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpClient;
import java.security.KeyPair;
import java.time.Duration;
import kr.ktb.zura.needu.notification.WebPushTestKeys;
import kr.ktb.zura.needu.notification.client.WebPushClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.json.JsonMapper;

class WebPushConfigTest {

    private final KeyPair vapidKeyPair = WebPushTestKeys.generateKeyPair();

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(WebPushConfig.class)
            .withBean(JsonMapper.class, () -> JsonMapper.builder().build())
            .withPropertyValues(
                    "notification.webpush.public-key=" + WebPushTestKeys.toPublicKeyProperty(vapidKeyPair),
                    "notification.webpush.private-key=" + WebPushTestKeys.toPrivateKeyProperty(vapidKeyPair),
                    "notification.webpush.subject=mailto:needu@example.com",
                    "notification.webpush.ttl=24h",
                    "notification.webpush.request-timeout=5s"
            );

    @Test
    void enabledWithValidVapidKeys_registersWebPushClient() {
        contextRunner
                .withPropertyValues("notification.webpush.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(WebPushClient.class);
                    assertThat(context.getBean(HttpClient.class).connectTimeout())
                            .contains(Duration.ofSeconds(5));
                });
    }

    @Test
    void disabled_doesNotRegisterWebPushBeans() {
        contextRunner
                .withPropertyValues("notification.webpush.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(WebPushClient.class);
                    assertThat(context).doesNotHaveBean(WebPushProperties.class);
                });
    }

    @Test
    void enabledPropertyMissing_doesNotRegisterWebPushClient() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(WebPushClient.class);
        });
    }

    @Test
    void enabledWithBlankPrivateKey_failsStartup() {
        contextRunner
                .withPropertyValues(
                        "notification.webpush.enabled=true",
                        "notification.webpush.private-key="
                )
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void enabledWithNonPositiveTtl_failsStartup() {
        contextRunner
                .withPropertyValues(
                        "notification.webpush.enabled=true",
                        "notification.webpush.ttl=0s"
                )
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void enabledWithMismatchedVapidKeyFormat_failsStartup() {
        contextRunner
                .withPropertyValues(
                        "notification.webpush.enabled=true",
                        "notification.webpush.private-key=" + WebPushTestKeys.toBase64Url(new byte[32])
                )
                .run(context -> assertThat(context).hasFailed());
    }
}
