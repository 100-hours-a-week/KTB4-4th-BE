package kr.ktb.zura.needu.anonymouschat.repository;

import java.util.UUID;

import kr.ktb.zura.needu.anonymouschat.entity.AnonymousMessage;
import kr.ktb.zura.needu.anonymouschat.entity.AnonymousRoom;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class AnonymousMessageRepositoryTest {

    @Autowired
    private AnonymousMessageRepository anonymousMessageRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void messageInRoom_existsByIdAndRoomId_returnsTrueOnlyForThatRoom() {
        AnonymousRoom room = persistRoom();
        AnonymousRoom otherRoom = persistRoom();
        AnonymousMessage message = entityManager.persistAndFlush(
                new AnonymousMessage(room, 1L, UUID.randomUUID(), "이 선물 어때?"));

        assertThat(anonymousMessageRepository.existsByIdAndRoomId(message.getId(), room.getId())).isTrue();
        assertThat(anonymousMessageRepository.existsByIdAndRoomId(message.getId(), otherRoom.getId())).isFalse();
    }

    private AnonymousRoom persistRoom() {
        return entityManager.persistAndFlush(new AnonymousRoom(10L, 1L, 2L, "익명의 수달"));
    }
}
