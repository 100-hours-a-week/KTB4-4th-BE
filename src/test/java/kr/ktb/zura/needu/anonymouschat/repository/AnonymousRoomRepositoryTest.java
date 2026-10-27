package kr.ktb.zura.needu.anonymouschat.repository;

import kr.ktb.zura.needu.anonymouschat.entity.AnonymousRoom;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

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

    private AnonymousRoom persistRoom() {
        return entityManager.persistAndFlush(new AnonymousRoom(10L, 1L, 2L, "익명의 수달"));
    }

    private AnonymousRoom reload(AnonymousRoom room) {
        entityManager.clear();
        return entityManager.find(AnonymousRoom.class, room.getId());
    }
}
