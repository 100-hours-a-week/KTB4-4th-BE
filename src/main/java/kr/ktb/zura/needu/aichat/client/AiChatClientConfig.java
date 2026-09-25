package kr.ktb.zura.needu.aichat.client;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "ai.server.mock-enabled", havingValue = "false", matchIfMissing = true)
public class AiChatClientConfig {

    @Bean
    public RestClient aiChatRestClient(
            RestClient.Builder builder,
            @Value("${AI_SERVER_BASE_URL}") String baseUrl,
            @Value("${ai.server.connect-timeout}") Duration connectTimeout,
            @Value("${ai.server.read-timeout}") Duration readTimeout
    ) {
        return build(builder, baseUrl, connectTimeout, readTimeout);
    }

    // 메시지 전송은 AI 답변 생성과 취향 추출을 모두 마친 뒤 응답하므로(AI 명세 3번) 다른 API보다 오래 기다린다.
    @Bean
    public RestClient aiChatMessageRestClient(
            RestClient.Builder builder,
            @Value("${AI_SERVER_BASE_URL}") String baseUrl,
            @Value("${ai.server.connect-timeout}") Duration connectTimeout,
            @Value("${ai.server.message-read-timeout}") Duration messageReadTimeout
    ) {
        return build(builder, baseUrl, connectTimeout, messageReadTimeout);
    }

    private RestClient build(RestClient.Builder builder, String baseUrl,
                             Duration connectTimeout, Duration readTimeout) {
        return builder
                .requestFactory(ClientHttpRequestFactoryBuilder.jdk()
                        .withHttpClientCustomizer(client -> client.version(HttpClient.Version.HTTP_1_1))
                        .build(
                                HttpClientSettings.defaults().withTimeouts(connectTimeout, readTimeout)))
                .baseUrl(baseUrl)
                .build();
    }
}
