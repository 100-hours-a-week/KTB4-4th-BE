package kr.ktb.zura.needu.aichat.config;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AiReplyQueueProperties.class)
public class AiReplyQueueConfig {

    private static final String UNKNOWN_HOST = "unknown-host";

    @Bean
    public AiReplyConsumerName aiReplyConsumerName() {
        return AiReplyConsumerName.generate(findHostName());
    }

    private String findHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return UNKNOWN_HOST;
        }
    }
}
