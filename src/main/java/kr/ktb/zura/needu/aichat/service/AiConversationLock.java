package kr.ktb.zura.needu.aichat.service;

import java.util.Optional;
import java.util.UUID;

import kr.ktb.zura.needu.aichat.repository.AiConversationLockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// AI 명세상 같은 대화방에는 이전 응답을 받은 뒤 다음 메시지를 보내야 하므로 대화방 단위로 동시 처리를 막는다.
@Slf4j
@Component
@RequiredArgsConstructor
public class AiConversationLock {

    private final AiConversationLockRepository aiConversationLockRepository;

    public Optional<Handle> tryLock(Long conversationId) {
        String ownerToken = UUID.randomUUID().toString();
        if (!aiConversationLockRepository.create(conversationId, ownerToken)) {
            return Optional.empty();
        }
        return Optional.of(new Handle(conversationId, ownerToken));
    }

    public boolean isLocked(Long conversationId) {
        return aiConversationLockRepository.exists(conversationId);
    }

    // 해제에 실패해도 TTL이 지나면 풀리기에, 원래 처리 결과나 예외를 가리지 않도록 로그만
    public void unlock(Handle handle) {
        try {
            aiConversationLockRepository.delete(handle.conversationId(), handle.ownerToken());
        } catch (RuntimeException e) {
            log.warn("AI conversation lock release failed. conversationId={}", handle.conversationId(), e);
        }
    }

    public record Handle(Long conversationId, String ownerToken) {
    }
}
