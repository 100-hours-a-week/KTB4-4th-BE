package kr.ktb.zura.needu.anonymouschat.config;

import kr.ktb.zura.needu.anonymouschat.support.AnonymousChatHandshakeInterceptor;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.config.SimpleBrokerRegistration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.StompWebSocketEndpointRegistration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_SELF;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AnonymousChatWebSocketConfigTest {

    @Test
    @SuppressWarnings("unchecked")
    void messageBrokerConfigured_enablesTenSecondHeartbeat() {
        AnonymousChatHandshakeInterceptor handshakeInterceptor = mock(AnonymousChatHandshakeInterceptor.class);
        ObjectProvider<TaskScheduler> taskSchedulerProvider = mock(ObjectProvider.class);
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        MessageBrokerRegistry registry = mock(MessageBrokerRegistry.class);
        SimpleBrokerRegistration simpleBroker = mock(SimpleBrokerRegistration.class);
        given(taskSchedulerProvider.getObject()).willReturn(taskScheduler);
        given(registry.enableSimpleBroker("/topic", "/queue")).willReturn(simpleBroker);
        given(simpleBroker.setHeartbeatValue(org.mockito.ArgumentMatchers.any(long[].class)))
                .willReturn(simpleBroker);
        AnonymousChatWebSocketConfig config =
                new AnonymousChatWebSocketConfig(handshakeInterceptor, taskSchedulerProvider, new String[0]);

        config.configureMessageBroker(registry);

        ArgumentCaptor<long[]> heartbeat = ArgumentCaptor.forClass(long[].class);
        verify(simpleBroker).setHeartbeatValue(heartbeat.capture());
        assertThat(heartbeat.getValue()).containsExactly(10_000, 10_000);
        verify(simpleBroker).setTaskScheduler(taskScheduler);
    }

    @Test
    void allowedOriginPatternsConfigured_registersPatternsOnEndpoint() {
        StompWebSocketEndpointRegistration endpoint = mock(StompWebSocketEndpointRegistration.class, RETURNS_SELF);
        StompEndpointRegistry registry = mock(StompEndpointRegistry.class);
        given(registry.addEndpoint("/ws/v1/anonymous-chats")).willReturn(endpoint);
        AnonymousChatHandshakeInterceptor handshakeInterceptor = mock(AnonymousChatHandshakeInterceptor.class);
        AnonymousChatWebSocketConfig config = createConfig(handshakeInterceptor, "http://localhost:3000");

        config.registerStompEndpoints(registry);

        verify(endpoint).setAllowedOriginPatterns("http://localhost:3000");
        verify(endpoint).addInterceptors(handshakeInterceptor);
    }

    @Test
    void allowedOriginPatternsEmpty_registersNoExtraOrigin() {
        StompWebSocketEndpointRegistration endpoint = mock(StompWebSocketEndpointRegistration.class, RETURNS_SELF);
        StompEndpointRegistry registry = mock(StompEndpointRegistry.class);
        given(registry.addEndpoint("/ws/v1/anonymous-chats")).willReturn(endpoint);
        AnonymousChatWebSocketConfig config = createConfig(mock(AnonymousChatHandshakeInterceptor.class));

        config.registerStompEndpoints(registry);

        verify(endpoint).setAllowedOriginPatterns();
    }

    @SuppressWarnings("unchecked")
    private AnonymousChatWebSocketConfig createConfig(AnonymousChatHandshakeInterceptor handshakeInterceptor,
                                                      String... allowedOriginPatterns) {
        return new AnonymousChatWebSocketConfig(
                handshakeInterceptor, mock(ObjectProvider.class), allowedOriginPatterns);
    }
}
