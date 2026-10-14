package kr.ktb.zura.needu.aichat.service;

import kr.ktb.zura.needu.aichat.sse.AiChatRedisEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
public class AiChatEventSubscriber implements MessageListener {

    private final JsonMapper jsonMapper;
    private final AiChatStreamService aiChatStreamService;

    public AiChatEventSubscriber(JsonMapper jsonMapper, AiChatStreamService aiChatStreamService) {
        this.jsonMapper = jsonMapper;
        this.aiChatStreamService = aiChatStreamService;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        AiChatRedisEvent event;
        try {
            event = jsonMapper.readValue(message.getBody(), AiChatRedisEvent.class);
        } catch (JacksonException exception) {
            log.warn("AI chat Redis event deserialization failed. reason={}",
                    exception.getClass().getSimpleName());
            return;
        }

        if (event.conversationId() == null || event.type() == null || event.data() == null) {
            log.warn("AI chat Redis event ignored by missing field. conversationId={}", event.conversationId());
            return;
        }

        aiChatStreamService.sendEvent(event);
    }
}
