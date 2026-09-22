package kr.ktb.zura.needu.aichat.service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

// AI 명세상 같은 대화방에는 이전 응답을 받은 뒤 다음 메시지를 보내야 하므로 대화방 단위로 동시 처리를 막는다.
// TODO: 현재는 단일 인스턴스 전제의 in-process 락이다. Redis 도입 시 SETNX + TTL 기반 분산 락으로 교체한다.
@Component
public class AiConversationLock {

    private final Set<Long> lockedConversationIds = ConcurrentHashMap.newKeySet();

    public boolean tryLock(Long conversationId) {
        return lockedConversationIds.add(conversationId);
    }

    public void unlock(Long conversationId) {
        lockedConversationIds.remove(conversationId);
    }
}
