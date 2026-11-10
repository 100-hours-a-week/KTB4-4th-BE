package kr.ktb.zura.needu.aichat.repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import kr.ktb.zura.needu.aichat.entity.AiReplyJob;
import kr.ktb.zura.needu.aichat.type.AiReplyJobStatus;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiReplyJobRepository extends JpaRepository<AiReplyJob, Long> {

    Optional<AiReplyJob> findByUserMessageId(Long userMessageId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from AiReplyJob j where j.id = :id")
    Optional<AiReplyJob> findByIdForUpdate(@Param("id") Long id);

    @Modifying
    @Query("update AiReplyJob j set j.streamEntryId = :streamEntryId "
            + "where j.id = :id and j.dispatchToken = :dispatchToken")
    int updateStreamEntryId(@Param("id") Long id, @Param("dispatchToken") String dispatchToken,
                            @Param("streamEntryId") String streamEntryId);

    boolean existsByAiChatRoomIdAndStatusIn(Long aiChatRoomId, Collection<AiReplyJobStatus> statuses);

    List<AiReplyJob> findAllByAiChatRoomIdAndStatusIn(Long aiChatRoomId, Collection<AiReplyJobStatus> statuses);

    List<AiReplyJob> findAllByStatusInAndUpdatedAtBeforeOrderByIdAsc(
            Collection<AiReplyJobStatus> statuses, LocalDateTime updatedAt, Limit limit);

    @Modifying
    @Query("delete from AiReplyJob j where j.aiChatRoom.id in :aiChatRoomIds")
    void deleteAllByAiChatRoomIdIn(@Param("aiChatRoomIds") List<Long> aiChatRoomIds);
}
