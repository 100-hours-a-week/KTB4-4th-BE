package kr.ktb.zura.needu.anonymouschat.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import kr.ktb.zura.needu.anonymouschat.entity.AnonymousMessage;
import kr.ktb.zura.needu.anonymouschat.entity.AnonymousRoom;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.Limit;
import org.springframework.test.util.ReflectionTestUtils;

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

    @Test
    void messagesInRooms_findAllLatestByRoomId_returnsOnlyRoomMessagesNewestFirst() {
        AnonymousRoom room = persistRoom();
        AnonymousRoom otherRoom = persistRoom();
        AnonymousMessage first = persistMessage(room, "첫 메시지");
        persistMessage(otherRoom, "다른 방 메시지");
        AnonymousMessage deleted = persistMessage(room, "삭제된 메시지");
        ReflectionTestUtils.setField(deleted, "deletedAt", LocalDateTime.of(2026, 10, 24, 1, 0));
        entityManager.persistAndFlush(deleted);
        AnonymousMessage second = persistMessage(room, "두 번째 메시지");
        AnonymousMessage third = persistMessage(room, "세 번째 메시지");

        List<AnonymousMessage> messages = anonymousMessageRepository.findAllLatestByRoomId(
                room.getId(), null, Limit.of(10));

        assertThat(messages).extracting(AnonymousMessage::getId)
                .containsExactly(third.getId(), second.getId(), first.getId());
    }

    @Test
    void cursorAndLimit_findAllLatestByRoomId_returnsOlderMessagesWithinLimit() {
        AnonymousRoom room = persistRoom();
        AnonymousMessage first = persistMessage(room, "첫 메시지");
        AnonymousMessage second = persistMessage(room, "두 번째 메시지");
        AnonymousMessage third = persistMessage(room, "세 번째 메시지");
        AnonymousMessage fourth = persistMessage(room, "네 번째 메시지");

        List<AnonymousMessage> messages = anonymousMessageRepository.findAllLatestByRoomId(
                room.getId(), fourth.getId(), Limit.of(2));

        assertThat(messages).extracting(AnonymousMessage::getId)
                .containsExactly(third.getId(), second.getId());
        assertThat(messages).extracting(AnonymousMessage::getId).doesNotContain(first.getId());
    }

    private AnonymousMessage persistMessage(AnonymousRoom room, String content) {
        return entityManager.persistAndFlush(new AnonymousMessage(room, 1L, UUID.randomUUID(), content));
    }

    private AnonymousRoom persistRoom() {
        return entityManager.persistAndFlush(new AnonymousRoom(10L, 1L, 2L, "익명의 수달", "otter", "mint"));
    }
}
