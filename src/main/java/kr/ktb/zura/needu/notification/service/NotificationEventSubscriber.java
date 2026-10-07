package kr.ktb.zura.needu.notification.service;

import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

// Redis에서 받은 알림 이벤트를 현재 인스턴스의 SSE 연결로 전달한다.
@Component
public class NotificationEventSubscriber implements MessageListener {

    private final JsonMapper jsonMapper;

    public NotificationEventSubscriber(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        // TODO: JSON 역직렬화 후 receiverUserId에 해당하는 로컬 SseEmitter로 전달
    }
}
