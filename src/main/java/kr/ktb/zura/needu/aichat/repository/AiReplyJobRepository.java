package kr.ktb.zura.needu.aichat.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import kr.ktb.zura.needu.aichat.entity.AiReplyJob;
import kr.ktb.zura.needu.aichat.type.AiReplyJobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiReplyJobRepository extends JpaRepository<AiReplyJob, Long> {

    Optional<AiReplyJob> findByUserMessageId(Long userMessageId);

    boolean existsByAiChatRoomIdAndStatusIn(Long aiChatRoomId, Collection<AiReplyJobStatus> statuses);

    boolean existsByAiChatRoomIdAndStatusInAndUpdatedAtAfter(
            Long aiChatRoomId, Collection<AiReplyJobStatus> statuses, LocalDateTime updatedAt);

    @Modifying
    @Query("delete from AiReplyJob j where j.aiChatRoom.id in :aiChatRoomIds")
    void deleteAllByAiChatRoomIdIn(@Param("aiChatRoomIds") List<Long> aiChatRoomIds);
}
