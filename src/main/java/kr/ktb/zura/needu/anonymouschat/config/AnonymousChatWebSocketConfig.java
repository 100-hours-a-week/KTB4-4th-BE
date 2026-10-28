package kr.ktb.zura.needu.anonymouschat.config;

import kr.ktb.zura.needu.anonymouschat.support.AnonymousChatHandshakeInterceptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
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

    public AnonymousChatWebSocketConfig(
            AnonymousChatHandshakeInterceptor handshakeInterceptor,
            @Qualifier("messageBrokerTaskScheduler") ObjectProvider<TaskScheduler> messageBrokerTaskScheduler
    ) {
        this.handshakeInterceptor = handshakeInterceptor;
        this.messageBrokerTaskScheduler = messageBrokerTaskScheduler;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/v1/anonymous-chats")
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
