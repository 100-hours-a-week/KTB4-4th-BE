package kr.ktb.zura.needu.common.config;

import kr.ktb.zura.needu.common.loadtest.LoadTestAccountProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(LoadTestAccountProperties.class)
public class LoadTestAccountConfig {
}
