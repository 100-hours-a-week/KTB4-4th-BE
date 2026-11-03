package kr.ktb.zura.needu.aichat.repository;

import java.util.Collection;
import java.util.Optional;

import kr.ktb.zura.needu.aichat.entity.AiReplyJob;
import kr.ktb.zura.needu.aichat.type.AiReplyJobStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiReplyJobRepository extends JpaRepository<AiReplyJob, Long> {

    Optional<AiReplyJob> findByUserMessageId(Long userMessageId);

    boolean existsByAiChatRoomIdAndStatusIn(Long aiChatRoomId, Collection<AiReplyJobStatus> statuses);
}
