package kr.ktb.zura.needu.anonymouschat.config;

import kr.ktb.zura.needu.anonymouschat.support.AnonymousChatHandshakeInterceptor;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.config.SimpleBrokerRegistration;
import org.springframework.scheduling.TaskScheduler;

import static org.assertj.core.api.Assertions.assertThat;
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
                new AnonymousChatWebSocketConfig(handshakeInterceptor, taskSchedulerProvider);

        config.configureMessageBroker(registry);

        ArgumentCaptor<long[]> heartbeat = ArgumentCaptor.forClass(long[].class);
        verify(simpleBroker).setHeartbeatValue(heartbeat.capture());
        assertThat(heartbeat.getValue()).containsExactly(10_000, 10_000);
        verify(simpleBroker).setTaskScheduler(taskScheduler);
    }
}
