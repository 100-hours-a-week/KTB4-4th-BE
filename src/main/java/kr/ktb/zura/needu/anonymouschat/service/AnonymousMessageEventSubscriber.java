package kr.ktb.zura.needu.anonymouschat.service;

import kr.ktb.zura.needu.anonymouschat.ws.AnonymousChatEventSender;
import kr.ktb.zura.needu.anonymouschat.ws.AnonymousMessageRedisEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
public class AnonymousMessageEventSubscriber implements MessageListener {

    private final JsonMapper jsonMapper;
    private final AnonymousChatEventSender eventSender;

    public AnonymousMessageEventSubscriber(JsonMapper jsonMapper, AnonymousChatEventSender eventSender) {
        this.jsonMapper = jsonMapper;
        this.eventSender = eventSender;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        AnonymousMessageRedisEvent event;
        try {
            event = jsonMapper.readValue(message.getBody(), AnonymousMessageRedisEvent.class);
        } catch (JacksonException exception) {
            log.warn("Anonymous message Redis event deserialization failed. reason={}",
                    exception.getClass().getSimpleName());
            return;
        }

        if (event.receiverUserId() == null || event.event() == null || !event.event().hasNonNull("type")) {
            log.warn("Anonymous message Redis event ignored by missing field. receiverUserId={}",
                    event.receiverUserId());
            return;
        }

        eventSender.sendToUser(event.receiverUserId(), event.event());
    }
}
