package kr.ktb.zura.needu.notification.service;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.CommonErrorCode;
import kr.ktb.zura.needu.common.response.RetryAfterResponse;
import kr.ktb.zura.needu.notification.exception.NotificationErrorCode;
import kr.ktb.zura.needu.notification.sse.NotificationRedisEvent;
import kr.ktb.zura.needu.user.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.node.ObjectNode;

@Service
public class NotificationStreamService {

    private static final int MAX_CONNECTIONS_PER_USER = 5;
    private static final long RETRY_AFTER_SECONDS = 10L;

    private final UserService userService;
    private final Duration streamTimeout;
    private final ConcurrentHashMap<Long, Set<SseEmitter>> emittersByUserId = new ConcurrentHashMap<>();

    public NotificationStreamService(
            UserService userService,
            @Value("${needu.notification.stream-timeout}") Duration streamTimeout
    ) {
        this.userService = userService;
        this.streamTimeout = streamTimeout;
    }

    public SseEmitter connect(Long userId) {
        userService.validateActiveUser(userId);

        SseEmitter emitter = new SseEmitter(streamTimeout.toMillis());
        emittersByUserId.compute(userId, (key, currentEmitters) -> {
            Set<SseEmitter> emitters = currentEmitters == null
                    ? ConcurrentHashMap.newKeySet()
                    : currentEmitters;
            if (emitters.size() >= MAX_CONNECTIONS_PER_USER) {
                throw new BusinessException(
                        NotificationErrorCode.NOTIFICATION_STREAM_LIMIT_EXCEEDED,
                        new RetryAfterResponse(RETRY_AFTER_SECONDS)
                );
            }
            emitters.add(emitter);
            return emitters;
        });

        Runnable removeEmitter = () -> removeEmitter(userId, emitter);
        emitter.onCompletion(removeEmitter);
        emitter.onTimeout(removeEmitter);
        emitter.onError(exception -> removeEmitter.run());

        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data(new ConnectedEvent(LocalDateTime.now(ZoneOffset.UTC))));
        } catch (IOException exception) {
            removeEmitter.run();
            throw new BusinessException(CommonErrorCode.COMMON_SERVICE_UNAVAILABLE, null, exception);
        }
        return emitter;
    }

    private void removeEmitter(Long userId, SseEmitter emitter) {
        emittersByUserId.computeIfPresent(userId, (key, emitters) -> {
            emitters.remove(emitter);
            return emitters.isEmpty() ? null : emitters;
        });
    }

    public void sendNotification(NotificationRedisEvent event) {
        // notificationId를 가진 JSON 객체인지는 NotificationEventSubscriber에서 검증한다.
        ObjectNode responseData = ((ObjectNode) event.data()).deepCopy();
        responseData.put("unreadCount", event.unreadCount());
        String notificationId = responseData.get("notificationId").asString();
        sendToUser(event.receiverUserId(), () -> SseEmitter.event()
                .id(notificationId)
                .name("notification.created")
                .data(responseData));
    }

    @Scheduled(fixedRateString = "${needu.notification.heartbeat-interval}")
    void sendHeartbeat() {
        HeartbeatEvent heartbeat = new HeartbeatEvent(LocalDateTime.now(ZoneOffset.UTC));
        emittersByUserId.keySet().forEach(userId -> sendToUser(userId, () -> SseEmitter.event()
                .name("heartbeat")
                .data(heartbeat)));
    }

    private void sendToUser(Long userId, Supplier<SseEmitter.SseEventBuilder> eventSupplier) {
        Set<SseEmitter> emitters = emittersByUserId.get(userId);
        if (emitters == null) {
            return;
        }

        emitters.forEach(emitter -> {
            try {
                emitter.send(eventSupplier.get());
            } catch (IOException | IllegalStateException exception) {
                emitter.completeWithError(exception);
                removeEmitter(userId, emitter);
            }
        });
    }

    private record ConnectedEvent(LocalDateTime connectedAt) {
    }

    private record HeartbeatEvent(LocalDateTime serverTime) {
    }
}
