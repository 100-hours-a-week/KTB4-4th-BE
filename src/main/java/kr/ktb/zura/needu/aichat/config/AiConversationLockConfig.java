package kr.ktb.zura.needu.aichat.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AiConversationLockProperties.class)
public class AiConversationLockConfig {
}
