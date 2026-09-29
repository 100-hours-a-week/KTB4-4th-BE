package kr.ktb.zura.needu.common.config;

import io.sentry.spring7.EnableSentry;
import org.springframework.context.annotation.Configuration;

@EnableSentry(
        dsn = "${SENTRY_DSN:}",
        sendDefaultPii = false
)
@Configuration
public class SentryConfiguration {
}
