package kr.ktb.zura.needu.aichat.service;

import java.util.Optional;

import kr.ktb.zura.needu.aichat.config.AiReplyQueueProperties;
import kr.ktb.zura.needu.aichat.repository.AiReplyHeartbeatResult;
import kr.ktb.zura.needu.aichat.repository.AiReplyPermitRepository;
import kr.ktb.zura.needu.aichat.repository.AiReplyStreamClaimResult;
import kr.ktb.zura.needu.aichat.repository.AiReplyStreamEntry;
import kr.ktb.zura.needu.aichat.repository.AiReplyStreamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiReplyQueueService {

    private final AiReplyStreamRepository aiReplyStreamRepository;
    private final AiReplyPermitRepository aiReplyPermitRepository;
    private final AiReplyJobService aiReplyJobService;
    private final AiReplyQueueProperties properties;

    // 작업은 DB에 저장돼 있음 => 스트림에 넣지 못해도 사용자 접수는 성공으로, 빠진 작업은 재투입 스케줄러가 다시 넣는다
    public void enqueue(Long jobId, String dispatchToken) {
        String streamEntryId;
        try {
            streamEntryId = aiReplyStreamRepository.add(jobId, dispatchToken);
        } catch (RuntimeException e) {
            log.warn("AI reply job enqueue failed. jobId={}", jobId, e);
            return;
        }
        try {
            aiReplyJobService.recordStreamEntry(jobId, dispatchToken, streamEntryId);
        } catch (RuntimeException e) {
            log.warn("AI reply stream entry record failed. jobId={}, streamEntryId={}", jobId, streamEntryId, e);
        }
    }

    public void createGroupIfAbsent() {
        aiReplyStreamRepository.createGroupIfAbsent();
    }

    public Optional<AiReplyStreamEntry> claimIdle(String consumerName) {
        AiReplyStreamClaimResult result = aiReplyStreamRepository.claimIdle(consumerName, properties.claimMinIdle());
        if (!result.deletedEntryIds().isEmpty()) {
            log.warn("AI reply stream entries deleted while pending. entryIds={}", result.deletedEntryIds());
        }
        if (!result.hasClaimed()) {
            return Optional.empty();
        }
        AiReplyStreamEntry claimed = result.claimed();
        log.info("AI reply job claimed. jobId={}, streamEntryId={}, deliveryCount={}",
                claimed.jobId(), claimed.entryId(), claimed.deliveryCount());
        return Optional.of(claimed);
    }

    public Optional<AiReplyStreamEntry> readNew(String consumerName) {
        return aiReplyStreamRepository.readNew(consumerName);
    }

    public AiReplyHeartbeatResult heartbeat(String consumerName, String streamEntryId) {
        return aiReplyStreamRepository.heartbeat(consumerName, streamEntryId);
    }

    public void acknowledge(String streamEntryId) {
        aiReplyStreamRepository.acknowledgeAndDelete(streamEntryId);
    }

    public boolean tryAcquirePermit(String permitId) {
        return aiReplyPermitRepository.acquire(permitId);
    }

    public void extendPermit(String permitId) {
        aiReplyPermitRepository.extend(permitId);
    }

    public void releasePermit(String permitId) {
        aiReplyPermitRepository.release(permitId);
    }
}
