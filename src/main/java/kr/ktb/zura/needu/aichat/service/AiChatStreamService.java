package kr.ktb.zura.needu.aichat.service;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import kr.ktb.zura.needu.aichat.entity.AiChatRoom;
import kr.ktb.zura.needu.aichat.sse.AiChatConnectedEvent;
import kr.ktb.zura.needu.aichat.sse.AiChatEventType;
import kr.ktb.zura.needu.aichat.sse.AiChatRedisEvent;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.CommonErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class AiChatStreamService {

    private static final String HEARTBEAT_COMMENT = "ping";

    private final AiGenerationStateService aiGenerationStateService;
    private final Duration streamTimeout;
    private final int maxPendingEvents;

    private final ExecutorService sendExecutor =
            Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("aichat-sse-", 0).factory());
    private final ConcurrentHashMap<Long, Set<AiChatStreamConnection>> connectionsByConversationId =
            new ConcurrentHashMap<>();

    public AiChatStreamService(
            AiGenerationStateService aiGenerationStateService,
            @Value("${ai.chat.stream-timeout}") Duration streamTimeout,
            @Value("${ai.chat.stream-max-pending-events}") int maxPendingEvents
    ) {
        this.aiGenerationStateService = aiGenerationStateService;
        this.streamTimeout = streamTimeout;
        this.maxPendingEvents = maxPendingEvents;
    }

    public SseEmitter connect(AiChatRoom room) {
        Long conversationId = room.getId();
        SseEmitter emitter = new SseEmitter(streamTimeout.toMillis());
        AiChatStreamConnection connection = new AiChatStreamConnection(
                conversationId, emitter, sendExecutor, maxPendingEvents,
                closedConnection -> removeConnection(conversationId, closedConnection));
        emitter.onCompletion(connection::close);
        emitter.onTimeout(connection::close);
        emitter.onError(exception -> connection.close());

        addConnection(conversationId, connection);
        try {
            Long pendingUserMessageId = aiGenerationStateService.findGeneratingUserMessageId(conversationId)
                    .orElse(null);
            connection.open(SseEmitter.event()
                    .name(AiChatEventType.CONNECTED.getEventName())
                    .data(new AiChatConnectedEvent(
                            conversationId,
                            room.getStatus(),
                            room.isInputLocked(),
                            pendingUserMessageId != null,
                            pendingUserMessageId)));
        } catch (IOException exception) {
            connection.close();
            throw new BusinessException(CommonErrorCode.COMMON_SERVICE_UNAVAILABLE, null, exception);
        } catch (RuntimeException exception) {
            connection.close();
            throw exception;
        }
        return emitter;
    }

    public void sendEvent(AiChatRedisEvent event) {
        sendToConversation(event.conversationId(), () -> SseEmitter.event()
                .name(event.type().getEventName())
                .data(event.data()));
    }

    @Scheduled(fixedRateString = "${ai.chat.stream-heartbeat-interval}")
    void sendHeartbeat() {
        connectionsByConversationId.keySet().forEach(conversationId -> sendToConversation(
                conversationId, () -> SseEmitter.event().comment(HEARTBEAT_COMMENT)));
    }

    private void addConnection(Long conversationId, AiChatStreamConnection connection) {
        connectionsByConversationId.compute(conversationId, (key, currentConnections) -> {
            Set<AiChatStreamConnection> connections = currentConnections == null
                    ? ConcurrentHashMap.newKeySet()
                    : currentConnections;
            connections.add(connection);
            return connections;
        });
    }

    private void removeConnection(Long conversationId, AiChatStreamConnection connection) {
        connectionsByConversationId.computeIfPresent(conversationId, (key, connections) -> {
            connections.remove(connection);
            return connections.isEmpty() ? null : connections;
        });
    }

    private void sendToConversation(Long conversationId, Supplier<SseEmitter.SseEventBuilder> eventSupplier) {
        Set<AiChatStreamConnection> connections = connectionsByConversationId.get(conversationId);
        if (connections == null) {
            return;
        }
        connections.forEach(connection -> connection.enqueue(eventSupplier.get()));
    }
}
