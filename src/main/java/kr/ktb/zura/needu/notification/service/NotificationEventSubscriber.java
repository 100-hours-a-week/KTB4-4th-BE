package kr.ktb.zura.needu.notification.service;

import kr.ktb.zura.needu.notification.sse.NotificationRedisEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

// Redis에서 받은 알림 이벤트를 현재 인스턴스의 SSE 연결로 전달한다.
@Slf4j
@Component
public class NotificationEventSubscriber implements MessageListener {

    private final JsonMapper jsonMapper;
    private final NotificationStreamService notificationStreamService;

    public NotificationEventSubscriber(JsonMapper jsonMapper, NotificationStreamService notificationStreamService) {
        this.jsonMapper = jsonMapper;
        this.notificationStreamService = notificationStreamService;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        NotificationRedisEvent event;
        try {
            event = jsonMapper.readValue(message.getBody(), NotificationRedisEvent.class);
        } catch (JacksonException exception) {
            // 알림 본문이 담겨 있으므로 원문 대신 예외 유형만 남긴다.
            log.warn("Notification Redis event deserialization failed. reason={}",
                    exception.getClass().getSimpleName());
            return;
        }

        if (event.receiverUserId() == null || event.data() == null) {
            log.warn("Notification Redis event ignored by missing field. receiverUserId={}", event.receiverUserId());
            return;
        }

        notificationStreamService.sendNotification(event);
    }
}
