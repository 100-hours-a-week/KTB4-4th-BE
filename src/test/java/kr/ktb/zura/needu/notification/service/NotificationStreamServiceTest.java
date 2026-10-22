package kr.ktb.zura.needu.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.response.RetryAfterResponse;
import kr.ktb.zura.needu.notification.exception.NotificationErrorCode;
import kr.ktb.zura.needu.notification.sse.NotificationRedisEvent;
import kr.ktb.zura.needu.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class NotificationStreamServiceTest {

    private static final Long USER_ID = 1L;

    private final UserService userService = mock(UserService.class);
    private final NotificationPresenceRegistry presenceRegistry = mock(NotificationPresenceRegistry.class);
    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final NotificationStreamService notificationStreamService =
            new NotificationStreamService(userService, presenceRegistry, Duration.ofMinutes(30));

    @Test
    void activeUser_connect_returnsEmitter() {
        SseEmitter emitter = notificationStreamService.connect(USER_ID);

        assertThat(emitter).isNotNull();
        assertThat(emitter.getTimeout()).isEqualTo(Duration.ofMinutes(30).toMillis());
        verify(userService).validateActiveUser(USER_ID);
    }

    @Test
    void activeUser_connect_registersPresence() {
        notificationStreamService.connect(USER_ID);

        verify(presenceRegistry).register(USER_ID);
    }

    @Test
    void streamLimitExceeded_connect_doesNotRegisterPresenceAgain() {
        for (int index = 0; index < 5; index++) {
            notificationStreamService.connect(USER_ID);
        }

        assertThatThrownBy(() -> notificationStreamService.connect(USER_ID))
                .isInstanceOf(BusinessException.class);
        verify(presenceRegistry, times(5)).register(USER_ID);
    }

    @Test
    void lastEmitterRemoved_sendNotification_unregistersPresence() throws Exception {
        SseEmitter emitter = mockEmitter(USER_ID);
        doThrow(new IOException("disconnected"))
                .when(emitter).send(any(SseEmitter.SseEventBuilder.class));

        notificationStreamService.sendNotification(notificationEvent());

        verify(presenceRegistry).unregister(USER_ID);
    }

    @Test
    void otherEmitterRemains_sendNotification_keepsPresence() throws Exception {
        SseEmitter failedEmitter = mockEmitter(USER_ID);
        doThrow(new IOException("disconnected"))
                .when(failedEmitter).send(any(SseEmitter.SseEventBuilder.class));
        SseEmitter liveEmitter = mock(SseEmitter.class);
        emittersOf(USER_ID).add(liveEmitter);

        notificationStreamService.sendNotification(notificationEvent());

        verify(liveEmitter).send(any(SseEmitter.SseEventBuilder.class));
        verify(presenceRegistry, never()).unregister(any());
    }

    @Test
    void connectedUsers_sendHeartbeat_refreshesPresence() {
        mockEmitter(USER_ID);
        mockEmitter(2L);

        notificationStreamService.sendHeartbeat();

        verify(presenceRegistry).refresh(Set.of(USER_ID, 2L));
    }

    @Test
    void fiveStreamsAlreadyConnected_connect_throwsTooManyRequests() {
        for (int index = 0; index < 5; index++) {
            notificationStreamService.connect(USER_ID);
        }

        assertThatThrownBy(() -> notificationStreamService.connect(USER_ID))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode())
                            .isEqualTo(NotificationErrorCode.NOTIFICATION_STREAM_LIMIT_EXCEEDED);
                    assertThat(exception.getData()).isEqualTo(new RetryAfterResponse(10));
                });
    }

    @Test
    void connectedUser_sendNotification_sendsCreatedEventWithUnreadCount() throws Exception {
        SseEmitter emitter = mockEmitter(USER_ID);
        NotificationRedisEvent event = new NotificationRedisEvent(
                USER_ID,
                jsonMapper.createObjectNode().put("notificationId", 10L).put("title", "새 알림"),
                3L
        );

        notificationStreamService.sendNotification(event);

        ArgumentCaptor<SseEmitter.SseEventBuilder> eventCaptor =
                ArgumentCaptor.forClass(SseEmitter.SseEventBuilder.class);
        verify(emitter).send(eventCaptor.capture());
        Set<SseEmitter.DataWithMediaType> eventParts = eventCaptor.getValue().build();
        assertThat(eventParts).extracting(part -> part.getData().toString())
                .anyMatch(part -> part.contains("id:10") && part.contains("event:notification.created"));
        assertThat(eventParts).extracting(SseEmitter.DataWithMediaType::getData)
                .filteredOn(JsonNode.class::isInstance)
                .singleElement()
                .satisfies(data -> assertThat(((JsonNode) data).get("unreadCount").asLong()).isEqualTo(3L));
    }

    @Test
    void emitterSendFails_sendNotification_removesFailedEmitter() throws Exception {
        SseEmitter emitter = mockEmitter(USER_ID);
        doThrow(new IOException("disconnected"))
                .when(emitter).send(any(SseEmitter.SseEventBuilder.class));
        NotificationRedisEvent event = new NotificationRedisEvent(
                USER_ID,
                jsonMapper.createObjectNode().put("notificationId", 10L),
                1L
        );

        notificationStreamService.sendNotification(event);
        notificationStreamService.sendNotification(event);

        verify(emitter, times(1)).send(any(SseEmitter.SseEventBuilder.class));
        verify(emitter, never()).completeWithError(any());
    }

    @Test
    void connectedUsers_sendHeartbeat_sendsToEveryLocalEmitter() throws Exception {
        SseEmitter firstEmitter = mockEmitter(USER_ID);
        SseEmitter secondEmitter = mockEmitter(2L);

        notificationStreamService.sendHeartbeat();

        verify(firstEmitter).send(any(SseEmitter.SseEventBuilder.class));
        verify(secondEmitter).send(any(SseEmitter.SseEventBuilder.class));
    }

    private SseEmitter mockEmitter(Long userId) {
        notificationStreamService.connect(userId);
        Set<SseEmitter> emitters = emittersOf(userId);
        emitters.clear();
        SseEmitter emitter = mock(SseEmitter.class);
        emitters.add(emitter);
        return emitter;
    }

    @SuppressWarnings("unchecked")
    private Set<SseEmitter> emittersOf(Long userId) {
        Map<Long, Set<SseEmitter>> emittersByUserId =
                (Map<Long, Set<SseEmitter>>) ReflectionTestUtils.getField(
                        notificationStreamService, "emittersByUserId");
        return emittersByUserId.get(userId);
    }

    private NotificationRedisEvent notificationEvent() {
        return new NotificationRedisEvent(
                USER_ID,
                jsonMapper.createObjectNode().put("notificationId", 10L),
                1L
        );
    }
}
