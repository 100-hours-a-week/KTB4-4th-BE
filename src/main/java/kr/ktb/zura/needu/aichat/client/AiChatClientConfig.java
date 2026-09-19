package kr.ktb.zura.needu.aichat.client;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
public class AiChatClientConfig {

    @Bean
    public RestClient aiChatRestClient(
            RestClient.Builder builder,
            @Value("${AI_SERVER_BASE_URL}") String baseUrl,
            @Value("${ai.server.connect-timeout}") Duration connectTimeout,
            @Value("${ai.server.read-timeout}") Duration readTimeout
    ) {
        return builder
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(
                        HttpClientSettings.defaults().withTimeouts(connectTimeout, readTimeout)))
                .baseUrl(baseUrl)
                .build();
    }
}
