package kr.ktb.zura.needu.anonymouschat.repository;

import java.time.LocalDateTime;
import kr.ktb.zura.needu.anonymouschat.entity.AnonymousReport;
import kr.ktb.zura.needu.anonymouschat.entity.AnonymousRoom;
import kr.ktb.zura.needu.anonymouschat.type.AnonymousReportReason;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class AnonymousReportRepositoryTest {

    private static final Long REPORTER_ID = 2L;

    @Autowired
    private AnonymousReportRepository anonymousReportRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void questionerReport_saveAndFlush_storesRecipientAsReportedUser() {
        AnonymousRoom room = persistRoom();

        AnonymousReport report = anonymousReportRepository.saveAndFlush(
                new AnonymousReport(1L, room, AnonymousReportReason.SPAM, null));
        entityManager.clear();

        assertThat(entityManager.find(AnonymousReport.class, report.getId()).getReportedUserId())
                .isEqualTo(REPORTER_ID);
    }

    @Test
    void newReport_saveAndFlush_persistsReport() {
        AnonymousRoom room = persistRoom();

        AnonymousReport report = anonymousReportRepository.saveAndFlush(
                new AnonymousReport(REPORTER_ID, room, AnonymousReportReason.ABUSE, "욕설을 보냈습니다."));
        entityManager.clear();

        AnonymousReport saved = entityManager.find(AnonymousReport.class, report.getId());
        assertThat(saved.getReporterUserId()).isEqualTo(REPORTER_ID);
        assertThat(saved.getReportedUserId()).isEqualTo(1L);
        assertThat(saved.getRoom().getId()).isEqualTo(room.getId());
        assertThat(saved.getReason()).isEqualTo(AnonymousReportReason.ABUSE);
        assertThat(saved.getDescription()).isEqualTo("욕설을 보냈습니다.");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void reportedRoom_existsByReporterUserIdAndRoomId_returnsTrueOnlyForThatReporter() {
        AnonymousRoom room = persistRoom();
        anonymousReportRepository.saveAndFlush(
                new AnonymousReport(REPORTER_ID, room, AnonymousReportReason.SPAM, null));

        assertThat(anonymousReportRepository.existsByReporterUserIdAndRoomId(REPORTER_ID, room.getId())).isTrue();
        assertThat(anonymousReportRepository.existsByReporterUserIdAndRoomId(1L, room.getId())).isFalse();
    }

    @Test
    void sameReporterAndRoom_saveAndFlush_throwsDataIntegrityViolation() {
        AnonymousRoom room = persistRoom();
        anonymousReportRepository.saveAndFlush(
                new AnonymousReport(REPORTER_ID, room, AnonymousReportReason.SPAM, null));

        assertThatThrownBy(() -> anonymousReportRepository.saveAndFlush(
                new AnonymousReport(REPORTER_ID, room, AnonymousReportReason.ABUSE, null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void reportsBeforeAndAfterBlock_countByReportedUserIdAndCreatedAtAfter_countsOnlyLaterReports() {
        LocalDateTime lastBlockedAt = LocalDateTime.of(2026, 10, 20, 0, 0);
        persistReportAt(lastBlockedAt.minusDays(1));
        persistReportAt(lastBlockedAt.plusDays(1));
        persistReportAt(lastBlockedAt.plusDays(2));

        assertThat(anonymousReportRepository.countByReportedUserId(1L)).isEqualTo(3);
        assertThat(anonymousReportRepository.countByReportedUserIdAndCreatedAtAfter(1L, lastBlockedAt))
                .isEqualTo(2);
    }

    private void persistReportAt(LocalDateTime createdAt) {
        AnonymousReport report = anonymousReportRepository.saveAndFlush(
                new AnonymousReport(REPORTER_ID, persistRoom(), AnonymousReportReason.SPAM, null));
        entityManager.getEntityManager()
                .createQuery("update AnonymousReport report set report.createdAt = :createdAt where report.id = :id")
                .setParameter("createdAt", createdAt)
                .setParameter("id", report.getId())
                .executeUpdate();
    }

    private AnonymousRoom persistRoom() {
        return entityManager.persistAndFlush(new AnonymousRoom(10L, 1L, REPORTER_ID, "익명의 수달", "otter", "mint"));
    }
}
