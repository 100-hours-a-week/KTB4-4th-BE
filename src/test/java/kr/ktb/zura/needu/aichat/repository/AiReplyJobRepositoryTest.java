package kr.ktb.zura.needu.aichat.repository;

import java.time.LocalDateTime;
import java.util.List;

import kr.ktb.zura.needu.aichat.entity.AiChatRoom;
import kr.ktb.zura.needu.aichat.entity.AiReplyJob;
import kr.ktb.zura.needu.aichat.type.AiReplyJobStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class AiReplyJobRepositoryTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final String LOCK_TOKEN = "lock-token";
    private static final List<AiReplyJobStatus> UNFINISHED = List.of(AiReplyJobStatus.QUEUED, AiReplyJobStatus.RUNNING);
    private static final LocalDateTime FINISHED_AT = LocalDateTime.of(2026, 11, 3, 12, 0);

    @Autowired
    private AiReplyJobRepository aiReplyJobRepository;

    @Autowired
    private TestEntityManager entityManager;

    private AiChatRoom room;

    @BeforeEach
    void setUp() {
        room = entityManager.persistAndFlush(AiChatRoom.reserve(USER_ID));
    }

    @Test
    void jobSaved_findByUserMessageId_returnsJobWithTimestamps() {
        AiReplyJob saved = aiReplyJobRepository.saveAndFlush(AiReplyJob.create(room, 201L, USER_ID, LOCK_TOKEN));
        entityManager.clear();

        AiReplyJob found = aiReplyJobRepository.findByUserMessageId(201L).orElseThrow();

        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getStatus()).isEqualTo(AiReplyJobStatus.QUEUED);
        assertThat(found.getDispatchToken()).isEqualTo(saved.getDispatchToken());
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
    }

    @Test
    void noJob_findByUserMessageId_returnsEmpty() {
        assertThat(aiReplyJobRepository.findByUserMessageId(999L)).isEmpty();
    }

    @Test
    void jobAlreadyExistsForMessage_save_violatesUniqueConstraint() {
        aiReplyJobRepository.saveAndFlush(AiReplyJob.create(room, 201L, USER_ID, LOCK_TOKEN));

        assertThatThrownBy(() -> aiReplyJobRepository.saveAndFlush(
                AiReplyJob.create(room, 201L, USER_ID, LOCK_TOKEN)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void queuedJobInRoom_existsByAiChatRoomIdAndStatusIn_returnsTrue() {
        aiReplyJobRepository.saveAndFlush(AiReplyJob.create(room, 201L, USER_ID, LOCK_TOKEN));

        assertThat(aiReplyJobRepository.existsByAiChatRoomIdAndStatusIn(room.getId(), UNFINISHED)).isTrue();
    }

    @Test
    void runningJobInRoom_existsByAiChatRoomIdAndStatusIn_returnsTrue() {
        AiReplyJob job = AiReplyJob.create(room, 201L, USER_ID, LOCK_TOKEN);
        job.start();
        aiReplyJobRepository.saveAndFlush(job);

        assertThat(aiReplyJobRepository.existsByAiChatRoomIdAndStatusIn(room.getId(), UNFINISHED)).isTrue();
    }

    @Test
    void onlyFinishedJobsInRoom_existsByAiChatRoomIdAndStatusIn_returnsFalse() {
        AiReplyJob succeeded = AiReplyJob.create(room, 201L, USER_ID, LOCK_TOKEN);
        succeeded.start();
        succeeded.succeed(FINISHED_AT);
        AiReplyJob failed = AiReplyJob.create(room, 202L, USER_ID, LOCK_TOKEN);
        failed.fail("AICHAT_REQUEST_TIMEOUT", FINISHED_AT);
        aiReplyJobRepository.saveAllAndFlush(List.of(succeeded, failed));

        assertThat(aiReplyJobRepository.existsByAiChatRoomIdAndStatusIn(room.getId(), UNFINISHED)).isFalse();
    }

    @Test
    void unfinishedJobOnlyInOtherRoom_existsByAiChatRoomIdAndStatusIn_returnsFalse() {
        AiChatRoom otherRoom = entityManager.persistAndFlush(AiChatRoom.reserve(OTHER_USER_ID));
        aiReplyJobRepository.saveAndFlush(AiReplyJob.create(otherRoom, 301L, OTHER_USER_ID, LOCK_TOKEN));

        assertThat(aiReplyJobRepository.existsByAiChatRoomIdAndStatusIn(room.getId(), UNFINISHED)).isFalse();
    }
}
