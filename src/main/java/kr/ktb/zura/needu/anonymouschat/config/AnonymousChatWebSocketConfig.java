package kr.ktb.zura.needu.anonymouschat.config;

import kr.ktb.zura.needu.anonymouschat.support.AnonymousChatHandshakeInterceptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration(proxyBeanMethods = false)
@EnableWebSocketMessageBroker
public class AnonymousChatWebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final AnonymousChatHandshakeInterceptor handshakeInterceptor;
    private final ObjectProvider<TaskScheduler> messageBrokerTaskScheduler;
    private final String[] allowedOriginPatterns;

    public AnonymousChatWebSocketConfig(
            AnonymousChatHandshakeInterceptor handshakeInterceptor,
            @Qualifier("messageBrokerTaskScheduler") ObjectProvider<TaskScheduler> messageBrokerTaskScheduler,
            @Value("${anonymous-chat.websocket.allowed-origin-patterns}") String[] allowedOriginPatterns
    ) {
        this.handshakeInterceptor = handshakeInterceptor;
        this.messageBrokerTaskScheduler = messageBrokerTaskScheduler;
        this.allowedOriginPatterns = allowedOriginPatterns.clone();
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 쿠키 인증이라 다른 origin을 열면 CSWSH에 노출, 비어 있으면 Spring 기본값대로 같은 origin만 허용
        registry.addEndpoint("/ws/v1/anonymous-chats")
                .setAllowedOriginPatterns(allowedOriginPatterns)
                .addInterceptors(handshakeInterceptor);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        registry.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(new long[]{10_000, 10_000})
                .setTaskScheduler(messageBrokerTaskScheduler.getObject());
    }
}
