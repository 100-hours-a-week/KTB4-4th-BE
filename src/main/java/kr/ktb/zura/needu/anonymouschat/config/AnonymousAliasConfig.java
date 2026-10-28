package kr.ktb.zura.needu.anonymouschat.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AnonymousAliasProperties.class)
public class AnonymousAliasConfig {
}
