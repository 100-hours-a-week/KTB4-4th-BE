package kr.ktb.zura.needu.aichat.service;

import kr.ktb.zura.needu.aichat.repository.AiReplyStreamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiReplyQueueService {

    private final AiReplyStreamRepository aiReplyStreamRepository;
    private final AiReplyJobService aiReplyJobService;

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
}
