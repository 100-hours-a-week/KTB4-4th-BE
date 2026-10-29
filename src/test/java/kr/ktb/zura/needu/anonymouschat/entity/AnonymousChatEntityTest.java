package kr.ktb.zura.needu.anonymouschat.entity;

import java.time.LocalDateTime;
import java.util.UUID;
import kr.ktb.zura.needu.anonymouschat.type.AnonymousRoomParticipantStatus;
import kr.ktb.zura.needu.anonymouschat.type.AnonymousRoomStatus;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class AnonymousChatEntityTest {

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void persistsRoomAndMessages() {
        AnonymousRoom room = entityManager.persistAndFlush(new AnonymousRoom(10L, 1L, 2L, "익명의 수달"));
        UUID clientMessageId = UUID.randomUUID();
        AnonymousMessage question = entityManager.persistAndFlush(
                new AnonymousMessage(room, 1L, clientMessageId, "이 선물 어때?"));
        AnonymousMessage reply = entityManager.persistAndFlush(
                new AnonymousMessage(room, 2L, clientMessageId, "좋아!"));
        entityManager.clear();

        AnonymousRoom savedRoom = entityManager.find(AnonymousRoom.class, room.getId());
        AnonymousMessage savedQuestion = entityManager.find(AnonymousMessage.class, question.getId());
        AnonymousMessage savedReply = entityManager.find(AnonymousMessage.class, reply.getId());

        assertThat(savedRoom.getProductId()).isEqualTo(10L);
        assertThat(savedRoom.getQuestionerUserId()).isEqualTo(1L);
        assertThat(savedRoom.getRecipientUserId()).isEqualTo(2L);
        assertThat(savedRoom.getQuestionerStatus()).isEqualTo(AnonymousRoomParticipantStatus.JOINED);
        assertThat(savedRoom.getRecipientStatus()).isEqualTo(AnonymousRoomParticipantStatus.JOINED);
        assertThat(savedRoom.getQuestionerAlias()).isEqualTo("익명의 수달");
        assertThat(savedRoom.getLastActivityAt()).isNotNull();
        assertThat(savedRoom.getCreatedAt()).isNotNull();
        assertThat(savedRoom.getOpenedAt()).isNull();
        assertThat(savedRoom.getExpiresAt()).isNull();
        assertThat(savedQuestion.getRoom().getId()).isEqualTo(room.getId());
        assertThat(savedQuestion.getSenderUserId()).isEqualTo(1L);
        assertThat(savedQuestion.getClientMessageId()).isEqualTo(clientMessageId);
        assertThat(savedQuestion.getContent()).isEqualTo("이 선물 어때?");
        assertThat(savedQuestion.getCreatedAt()).isNotNull();
        assertThat(savedQuestion.getDeletedAt()).isNull();
        assertThat(savedReply.getSenderUserId()).isEqualTo(2L);
    }

    @Test
    void newRoom_persist_startsActiveWithoutEndInfo() {
        AnonymousRoom room = entityManager.persistAndFlush(new AnonymousRoom(10L, 1L, 2L, "익명의 수달"));
        entityManager.clear();

        AnonymousRoom savedRoom = entityManager.find(AnonymousRoom.class, room.getId());

        assertThat(savedRoom.getStatus()).isEqualTo(AnonymousRoomStatus.ACTIVE);
        assertThat(savedRoom.isEnded()).isFalse();
        assertThat(savedRoom.getEndedAt()).isNull();
        assertThat(savedRoom.getEndedByUserId()).isNull();
    }

    @Test
    void questioner_leave_savesOnlyQuestionerStatusAsLeft() {
        AnonymousRoom room = entityManager.persistAndFlush(new AnonymousRoom(10L, 1L, 2L, "익명의 수달"));

        LocalDateTime expiresAt = LocalDateTime.of(2026, 10, 29, 10, 0);
        room.leave(1L, expiresAt);
        entityManager.flush();
        entityManager.clear();

        AnonymousRoom savedRoom = entityManager.find(AnonymousRoom.class, room.getId());
        assertThat(savedRoom.getQuestionerStatus()).isEqualTo(AnonymousRoomParticipantStatus.LEFT);
        assertThat(savedRoom.getRecipientStatus()).isEqualTo(AnonymousRoomParticipantStatus.JOINED);
        assertThat(savedRoom.getStatus()).isEqualTo(AnonymousRoomStatus.EXPIRED);
        assertThat(savedRoom.getExpiresAt()).isEqualTo(expiresAt);
    }

    @Test
    void activeRoom_end_savesEndedStatusAndWhoEndedIt() {
        AnonymousRoom room = entityManager.persistAndFlush(new AnonymousRoom(10L, 1L, 2L, "익명의 수달"));
        LocalDateTime endedAt = LocalDateTime.of(2026, 10, 25, 8, 30);

        boolean ended = room.end(2L, endedAt);
        entityManager.flush();
        entityManager.clear();

        AnonymousRoom savedRoom = entityManager.find(AnonymousRoom.class, room.getId());
        assertThat(ended).isTrue();
        assertThat(savedRoom.getStatus()).isEqualTo(AnonymousRoomStatus.ENDED);
        assertThat(savedRoom.getEndedByUserId()).isEqualTo(2L);
        assertThat(savedRoom.getEndedAt()).isEqualTo(endedAt);
    }

    @Test
    void endedRoom_end_keepsFirstEndInfo() {
        AnonymousRoom room = new AnonymousRoom(10L, 1L, 2L, "익명의 수달");
        LocalDateTime firstEndedAt = LocalDateTime.of(2026, 10, 25, 8, 30);
        room.end(2L, firstEndedAt);

        boolean endedAgain = room.end(1L, firstEndedAt.plusMinutes(5));

        assertThat(endedAgain).isFalse();
        assertThat(room.getEndedByUserId()).isEqualTo(2L);
        assertThat(room.getEndedAt()).isEqualTo(firstEndedAt);
    }

    @Test
    void rejectsDuplicateClientMessageForSameRoomAndSender() {
        AnonymousRoom room = entityManager.persistAndFlush(new AnonymousRoom(10L, 1L, 2L, "익명의 수달"));
        UUID clientMessageId = UUID.randomUUID();
        entityManager.persistAndFlush(new AnonymousMessage(room, 1L, clientMessageId, "이 선물 어때?"));

        assertThatThrownBy(() -> entityManager.persistAndFlush(
                new AnonymousMessage(room, 1L, clientMessageId, "이 선물 어때?")))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void rejectsRoomWithSameQuestionerAndRecipient() {
        assertThatThrownBy(() -> entityManager.persistAndFlush(new AnonymousRoom(10L, 1L, 1L, "익명의 수달")))
                .isInstanceOf(ConstraintViolationException.class);
    }
}
