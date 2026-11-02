package kr.ktb.zura.needu.notification.service;

import java.util.List;
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

        if (event.data() == null || !event.data().isObject() || event.recipients() == null) {
            log.warn("Notification Redis event ignored by missing field. recipientCount={}",
                    event.recipients() == null ? 0 : event.recipients().size());
            return;
        }

        // 한 메시지에 여러 사용자가 묶여 있으므로 잘못된 수신자만 빼고 나머지에게는 전달한다
        List<NotificationRedisEvent.Recipient> validRecipients = event.recipients().stream()
                .filter(this::isValidRecipient)
                .toList();
        int invalidCount = event.recipients().size() - validRecipients.size();
        if (invalidCount > 0) {
            log.warn("Notification Redis recipients ignored by missing field. invalidCount={}, recipientCount={}",
                    invalidCount, event.recipients().size());
        }
        if (validRecipients.isEmpty()) {
            return;
        }

        log.info("Notification Redis event received. type={}, recipientCount={}",
                event.data().path("type").asString(), validRecipients.size());
        notificationStreamService.sendNotification(new NotificationRedisEvent(event.data(), validRecipients));
    }

    private boolean isValidRecipient(NotificationRedisEvent.Recipient recipient) {
        return recipient != null
                && recipient.receiverUserId() != null
                && recipient.data() != null
                && recipient.data().isObject()
                && recipient.data().hasNonNull("notificationId");
    }
}
