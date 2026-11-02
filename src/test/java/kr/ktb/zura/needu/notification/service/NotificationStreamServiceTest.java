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
import java.util.List;
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
    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final NotificationStreamService notificationStreamService =
            new NotificationStreamService(userService, Duration.ofMinutes(30));

    @Test
    void activeUser_connect_returnsEmitter() {
        SseEmitter emitter = notificationStreamService.connect(USER_ID);

        assertThat(emitter).isNotNull();
        assertThat(emitter.getTimeout()).isEqualTo(Duration.ofMinutes(30).toMillis());
        verify(userService).validateActiveUser(USER_ID);
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
                jsonMapper.createObjectNode().put("title", "새 알림"),
                List.of(recipient(USER_ID, 10L, 3L))
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
                .satisfies(data -> {
                    JsonNode responseData = (JsonNode) data;
                    assertThat(responseData.get("title").asString()).isEqualTo("새 알림");
                    assertThat(responseData.get("notificationId").asLong()).isEqualTo(10L);
                    assertThat(responseData.get("unreadCount").asLong()).isEqualTo(3L);
                });
    }

    @Test
    void recipientsOnSeveralInstances_sendNotification_sendsOnlyToLocalUsersWithOwnValues() throws Exception {
        SseEmitter firstEmitter = mockEmitter(USER_ID);
        SseEmitter secondEmitter = mockEmitter(2L);
        NotificationRedisEvent event = new NotificationRedisEvent(
                jsonMapper.createObjectNode().put("title", "친구의 생일이 7일 남았어요."),
                List.of(recipient(USER_ID, 10L, 3L), recipient(2L, 11L, 1L), recipient(3L, 12L, 5L))
        );

        notificationStreamService.sendNotification(event);

        assertThat(sentData(firstEmitter).get("notificationId").asLong()).isEqualTo(10L);
        assertThat(sentData(firstEmitter).get("unreadCount").asLong()).isEqualTo(3L);
        assertThat(sentData(secondEmitter).get("notificationId").asLong()).isEqualTo(11L);
        assertThat(sentData(secondEmitter).get("unreadCount").asLong()).isEqualTo(1L);
        assertThat(sentData(secondEmitter).get("title").asString()).isEqualTo("친구의 생일이 7일 남았어요.");
    }

    @Test
    void emitterSendFails_sendNotification_removesFailedEmitter() throws Exception {
        SseEmitter emitter = mockEmitter(USER_ID);
        doThrow(new IOException("disconnected"))
                .when(emitter).send(any(SseEmitter.SseEventBuilder.class));
        NotificationRedisEvent event = new NotificationRedisEvent(
                jsonMapper.createObjectNode(),
                List.of(recipient(USER_ID, 10L, 1L))
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

    private NotificationRedisEvent.Recipient recipient(Long receiverUserId, Long notificationId, long unreadCount) {
        return new NotificationRedisEvent.Recipient(
                receiverUserId,
                jsonMapper.createObjectNode().put("notificationId", notificationId),
                unreadCount
        );
    }

    private JsonNode sentData(SseEmitter emitter) throws IOException {
        ArgumentCaptor<SseEmitter.SseEventBuilder> eventCaptor =
                ArgumentCaptor.forClass(SseEmitter.SseEventBuilder.class);
        verify(emitter).send(eventCaptor.capture());
        return (JsonNode) eventCaptor.getValue().build().stream()
                .map(SseEmitter.DataWithMediaType::getData)
                .filter(JsonNode.class::isInstance)
                .findFirst()
                .orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private SseEmitter mockEmitter(Long userId) {
        notificationStreamService.connect(userId);
        Map<Long, Set<SseEmitter>> emittersByUserId =
                (Map<Long, Set<SseEmitter>>) ReflectionTestUtils.getField(
                        notificationStreamService, "emittersByUserId");
        Set<SseEmitter> emitters = emittersByUserId.get(userId);
        emitters.clear();
        SseEmitter emitter = mock(SseEmitter.class);
        emitters.add(emitter);
        return emitter;
    }
}
