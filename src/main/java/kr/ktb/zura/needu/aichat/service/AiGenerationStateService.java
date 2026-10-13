package kr.ktb.zura.needu.aichat.service;

import java.util.Optional;
import kr.ktb.zura.needu.aichat.repository.AiGenerationStateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiGenerationStateService {

    private final AiGenerationStateRepository aiGenerationStateRepository;

    public void startGenerating(Long conversationId, Long userMessageId) {
        aiGenerationStateRepository.save(conversationId, userMessageId);
    }

    // 해제에 실패해도 TTL이 지나면 풀림 => 답변 결과나 원래 예외를 가리지 않도록 경고 로그만
    public void finishGenerating(Long conversationId, Long userMessageId) {
        try {
            aiGenerationStateRepository.delete(conversationId, userMessageId);
        } catch (RuntimeException e) {
            log.warn("AI generation state release failed. conversationId={}, userMessageId={}",
                    conversationId, userMessageId, e);
        }
    }

    public Optional<Long> findGeneratingUserMessageId(Long conversationId) {
        return aiGenerationStateRepository.findUserMessageId(conversationId);
    }

    public boolean isGenerating(Long conversationId, Long userMessageId) {
        return findGeneratingUserMessageId(conversationId)
                .filter(userMessageId::equals)
                .isPresent();
    }
}
