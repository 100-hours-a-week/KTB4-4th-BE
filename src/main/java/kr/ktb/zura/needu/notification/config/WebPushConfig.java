package kr.ktb.zura.needu.notification.config;

import com.zerodeplibs.webpush.VAPIDKeyPair;
import com.zerodeplibs.webpush.VAPIDKeyPairs;
import com.zerodeplibs.webpush.key.PrivateKeySources;
import com.zerodeplibs.webpush.key.PublicKeySources;
import java.net.http.HttpClient;
import java.util.Base64;
import kr.ktb.zura.needu.notification.client.WebPushClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "notification.webpush.enabled", havingValue = "true")
@EnableConfigurationProperties(WebPushProperties.class)
public class WebPushConfig {

    @Bean
    public VAPIDKeyPair vapidKeyPair(WebPushProperties properties) {
        return VAPIDKeyPairs.of(
                PrivateKeySources.ofPKCS8Bytes(Base64.getDecoder().decode(properties.privateKey())),
                PublicKeySources.ofUncompressedBytes(Base64.getUrlDecoder().decode(properties.publicKey()))
        );
    }

    @Bean
    public HttpClient webPushHttpClient(WebPushProperties properties) {
        return HttpClient.newBuilder()
                .connectTimeout(properties.requestTimeout())
                .build();
    }

    @Bean
    public WebPushClient webPushClient(
            HttpClient webPushHttpClient,
            VAPIDKeyPair vapidKeyPair,
            WebPushProperties properties,
            JsonMapper jsonMapper
    ) {
        return new WebPushClient(webPushHttpClient, vapidKeyPair, properties, jsonMapper);
    }
}
