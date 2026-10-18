package kr.ktb.zura.needu.aichat.service;

import kr.ktb.zura.needu.aichat.sse.AiChatEventType;
import kr.ktb.zura.needu.aichat.sse.AiChatRedisEvent;
import kr.ktb.zura.needu.common.redis.RedisKeyGenerator;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

// AI 채팅 이벤트를 JSON으로 직렬화해 모든 BE 인스턴스에 발행한다.
@Component
public class AiChatEventPublisher {

    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;
    private final RedisKeyGenerator keyGenerator;

    public AiChatEventPublisher(
            StringRedisTemplate redisTemplate,
            JsonMapper jsonMapper,
            RedisKeyGenerator keyGenerator
    ) {
        this.redisTemplate = redisTemplate;
        this.jsonMapper = jsonMapper;
        this.keyGenerator = keyGenerator;
    }

    public void publish(Long conversationId, AiChatEventType type, Object data) {
        try {
            AiChatRedisEvent event = new AiChatRedisEvent(conversationId, type, jsonMapper.valueToTree(data));
            redisTemplate.convertAndSend(
                    keyGenerator.generate("aichat", "events"),
                    jsonMapper.writeValueAsString(event)
            );
        } catch (JacksonException exception) {
            throw new IllegalStateException("Failed to serialize AI chat Redis event.", exception);
        }
    }
}
