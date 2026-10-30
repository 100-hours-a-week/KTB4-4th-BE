package kr.ktb.zura.needu.anonymouschat.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import kr.ktb.zura.needu.anonymouschat.entity.AnonymousRoom;
import kr.ktb.zura.needu.anonymouschat.type.AnonymousRoomStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class AnonymousRoomRepositoryTest {

    @Autowired
    private AnonymousRoomRepository anonymousRoomRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void noReadPosition_updateQuestionerLastReadMessageId_setsPosition() {
        AnonymousRoom room = persistRoom();

        int updated = anonymousRoomRepository.updateQuestionerLastReadMessageId(room.getId(), 5L);

        assertThat(updated).isEqualTo(1);
        AnonymousRoom saved = reload(room);
        assertThat(saved.getQuestionerLastReadMessageId()).isEqualTo(5L);
        assertThat(saved.getRecipientLastReadMessageId()).isNull();
    }

    @Test
    void largerMessageId_updateRecipientLastReadMessageId_movesPositionForward() {
        AnonymousRoom room = persistRoom();
        anonymousRoomRepository.updateRecipientLastReadMessageId(room.getId(), 5L);

        int updated = anonymousRoomRepository.updateRecipientLastReadMessageId(room.getId(), 8L);

        assertThat(updated).isEqualTo(1);
        assertThat(reload(room).getRecipientLastReadMessageId()).isEqualTo(8L);
    }

    @Test
    void smallerOrSameMessageId_updateRecipientLastReadMessageId_keepsPosition() {
        AnonymousRoom room = persistRoom();
        anonymousRoomRepository.updateRecipientLastReadMessageId(room.getId(), 8L);

        int smaller = anonymousRoomRepository.updateRecipientLastReadMessageId(room.getId(), 5L);
        int same = anonymousRoomRepository.updateRecipientLastReadMessageId(room.getId(), 8L);

        assertThat(smaller).isZero();
        assertThat(same).isZero();
        assertThat(reload(room).getRecipientLastReadMessageId()).isEqualTo(8L);
    }

    @Test
    void activeRooms_findAllActiveQuestionerAliases_returnsOnlyActiveAliases() {
        entityManager.persist(new AnonymousRoom(10L, 1L, 2L, "익명의 수달", "otter", "mint"));
        AnonymousRoom expiredRoom = new AnonymousRoom(20L, 3L, 2L, "익명의 여우", "fox", "mint");
        ReflectionTestUtils.setField(expiredRoom, "expiresAt", LocalDateTime.now());
        entityManager.persistAndFlush(expiredRoom);

        Set<String> aliases = anonymousRoomRepository.findAllActiveQuestionerAliasesByRecipientUserId(2L);

        assertThat(aliases).containsExactly("익명의 수달");
    }

    @Test
    void mixedRecipients_findAllByIdInAndRecipientUserId_returnsOnlyOwnedRooms() {
        AnonymousRoom own = entityManager.persistAndFlush(
                new AnonymousRoom(10L, 1L, 2L, "익명의 수달", "otter", "mint"));
        AnonymousRoom others = entityManager.persistAndFlush(
                new AnonymousRoom(20L, 3L, 4L, "익명의 여우", "fox", "mint"));

        List<AnonymousRoom> rooms = anonymousRoomRepository.findAllByIdInAndRecipientUserId(
                List.of(own.getId(), others.getId()), 2L);

        assertThat(rooms).extracting(AnonymousRoom::getId).containsExactly(own.getId());
    }

    @Test
    void mixedRooms_findAllParticipatingByUserIdAndStatusForUpdate_returnsOnlyActiveParticipatingRooms() {
        AnonymousRoom askedRoom = entityManager.persist(new AnonymousRoom(10L, 1L, 2L, "익명의 수달", "otter", "mint"));
        AnonymousRoom receivedRoom = entityManager.persist(new AnonymousRoom(20L, 3L, 1L, "익명의 여우", "fox", "mint"));
        AnonymousRoom endedRoom = new AnonymousRoom(30L, 1L, 4L, "익명의 토끼", "rabbit", "mint");
        endedRoom.end(1L, LocalDateTime.of(2026, 10, 28, 1, 0));
        entityManager.persist(endedRoom);
        entityManager.persistAndFlush(new AnonymousRoom(40L, 5L, 6L, "익명의 고양이", "cat", "mint"));

        List<AnonymousRoom> rooms = anonymousRoomRepository.findAllParticipatingByUserIdAndStatusForUpdate(
                1L, AnonymousRoomStatus.ACTIVE);

        assertThat(rooms).extracting(AnonymousRoom::getId)
                .containsExactlyInAnyOrder(askedRoom.getId(), receivedRoom.getId());
    }

    private AnonymousRoom persistRoom() {
        return entityManager.persistAndFlush(new AnonymousRoom(10L, 1L, 2L, "익명의 수달", "otter", "mint"));
    }

    private AnonymousRoom reload(AnonymousRoom room) {
        entityManager.clear();
        return entityManager.find(AnonymousRoom.class, room.getId());
    }
}
