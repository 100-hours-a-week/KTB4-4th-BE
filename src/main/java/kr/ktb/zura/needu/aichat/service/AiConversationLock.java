package kr.ktb.zura.needu.aichat.service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

// AI 명세상 같은 대화방에는 이전 응답을 받은 뒤 다음 메시지를 보내야 하므로 대화방 단위로 동시 처리를 막는다.
// TODO: Redis 교체 시 tryLock은 Handle을 반환하고 unlock은 해당 Handle의 ownerToken을 검증한다.
@Component
public class AiConversationLock {

    private final Set<Long> lockedConversationIds = ConcurrentHashMap.newKeySet();

    public boolean tryLock(Long conversationId) {
        return lockedConversationIds.add(conversationId);
    }

    public void unlock(Long conversationId) {
        lockedConversationIds.remove(conversationId);
    }

    public record Handle(Long conversationId, String ownerToken) {
    }
}
