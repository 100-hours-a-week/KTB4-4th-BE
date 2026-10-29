package kr.ktb.zura.needu.anonymouschat.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import kr.ktb.zura.needu.anonymouschat.entity.AnonymousMessage;
import kr.ktb.zura.needu.anonymouschat.entity.AnonymousRoom;
import kr.ktb.zura.needu.anonymouschat.type.AnonymousRoomStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.Limit;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class AnonymousChatRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private AnonymousRoomRepository anonymousRoomRepository;

    @Autowired
    private AnonymousMessageRepository anonymousMessageRepository;

    @Test
    void roomsWithDifferentRoles_findAllRooms_returnsLatestFirstAndAppliesCursor() {
        AnonymousRoom olderAskedRoom = persistRoom(10L, 1L, 2L, "익명의 수달");
        AnonymousRoom newerAskedRoom = persistRoom(20L, 1L, 3L, "익명의 여우");
        AnonymousRoom receivedRoom = persistRoom(30L, 4L, 1L, "익명의 토끼");
        LocalDateTime olderActivityAt = LocalDateTime.of(2099, 10, 24, 13, 0);
        LocalDateTime newerActivityAt = LocalDateTime.of(2099, 10, 24, 15, 0);
        updateActivityAt(olderAskedRoom, olderActivityAt);
        updateActivityAt(newerAskedRoom, newerActivityAt);
        updateActivityAt(receivedRoom, newerActivityAt.plusHours(1));

        List<AnonymousRoomListRow> askedRooms = anonymousRoomRepository.findAllVisibleAskedByUserId(
                1L, null, null, Limit.of(10));
        List<AnonymousRoomListRow> nextAskedRooms = anonymousRoomRepository.findAllVisibleAskedByUserId(
                1L, newerActivityAt, newerAskedRoom.getId(), Limit.of(10));
        List<AnonymousRoomListRow> receivedRooms = anonymousRoomRepository.findAllVisibleReceivedByUserId(
                1L, null, null, Limit.of(10));

        assertThat(askedRooms).extracting(AnonymousRoomListRow::roomId)
                .containsExactly(newerAskedRoom.getId(), olderAskedRoom.getId());
        assertThat(nextAskedRooms).extracting(AnonymousRoomListRow::roomId)
                .containsExactly(olderAskedRoom.getId());
        assertThat(receivedRooms).extracting(AnonymousRoomListRow::roomId)
                .containsExactly(receivedRoom.getId());
    }

    @Test
    void endedOrLeftRooms_findAllVisibleRooms_returnsJoinedActiveOrExpiredRoomsForBothRoles() {
        AnonymousRoom activeAskedRoom = persistRoom(10L, 1L, 2L, "익명의 수달");
        AnonymousRoom endedAskedRoom = persistRoom(20L, 1L, 3L, "익명의 여우");
        AnonymousRoom leftAskedRoom = persistRoom(30L, 1L, 4L, "익명의 토끼");
        AnonymousRoom partnerLeftAskedRoom = persistRoom(40L, 1L, 7L, "익명의 여우");
        AnonymousRoom activeReceivedRoom = persistRoom(30L, 4L, 1L, "익명의 토끼");
        AnonymousRoom endedReceivedRoom = persistRoom(40L, 5L, 1L, "익명의 고양이");
        AnonymousRoom leftReceivedRoom = persistRoom(50L, 6L, 1L, "익명의 사슴");
        AnonymousRoom partnerLeftReceivedRoom = persistRoom(60L, 8L, 1L, "익명의 수달");
        endedAskedRoom.end(1L, LocalDateTime.now());
        endedReceivedRoom.end(5L, LocalDateTime.now());
        LocalDateTime expiresAt = LocalDateTime.now();
        leftAskedRoom.leave(1L, expiresAt);
        leftReceivedRoom.leave(1L, expiresAt);
        partnerLeftAskedRoom.leave(7L, expiresAt);
        partnerLeftReceivedRoom.leave(8L, expiresAt);
        entityManager.flush();

        List<AnonymousRoomListRow> askedRooms = anonymousRoomRepository.findAllVisibleAskedByUserId(
                1L, null, null, Limit.of(10));
        List<AnonymousRoomListRow> receivedRooms = anonymousRoomRepository.findAllVisibleReceivedByUserId(
                1L, null, null, Limit.of(10));

        assertThat(askedRooms).extracting(AnonymousRoomListRow::roomId)
                .containsExactlyInAnyOrder(activeAskedRoom.getId(), partnerLeftAskedRoom.getId());
        assertThat(receivedRooms).extracting(AnonymousRoomListRow::roomId)
                .containsExactlyInAnyOrder(activeReceivedRoom.getId(), partnerLeftReceivedRoom.getId());
    }

    @Test
    void endedOrExpiredRooms_findLatestActiveRoom_returnsOnlyValidRoom() {
        AnonymousRoom activeRoom = persistRoom(10L, 1L, 2L, "익명의 수달");
        AnonymousRoom expiredRoom = persistRoom(10L, 1L, 2L, "익명의 여우");
        expiredRoom.leave(1L, LocalDateTime.now());
        entityManager.flush();
        AnonymousRoom endedRoom = persistRoom(10L, 1L, 2L, "익명의 토끼");
        endedRoom.end(1L, LocalDateTime.now());
        entityManager.flush();

        assertThat(anonymousRoomRepository
                .findFirstByProductIdAndQuestionerUserIdAndRecipientUserIdAndStatusAndExpiresAtIsNullOrderByIdDesc(
                        10L, 1L, 2L, AnonymousRoomStatus.ACTIVE))
                .contains(activeRoom);
    }

    @Test
    void messagesInRooms_findSummaries_returnsLastMessageAndRoleBasedUnreadCount() {
        AnonymousRoom room = persistRoom(10L, 1L, 2L, "익명의 수달");
        persistMessage(room, 1L, "내가 보낸 메시지");
        AnonymousMessage firstReceivedMessage = persistMessage(room, 2L, "첫 번째 받은 메시지");
        AnonymousMessage lastMessage = persistMessage(room, 2L, "마지막 받은 메시지");

        List<AnonymousRoomLastMessage> lastMessages =
                anonymousMessageRepository.findAllLastMessagesByRoomIdIn(List.of(room.getId()));
        List<AnonymousRoomUnreadCount> unreadCounts =
                anonymousMessageRepository.countAllUnreadByRoomIdIn(List.of(room.getId()), 1L);
        anonymousRoomRepository.updateQuestionerLastReadMessageId(room.getId(), firstReceivedMessage.getId());
        List<AnonymousRoomUnreadCount> unreadCountsAfterRead =
                anonymousMessageRepository.countAllUnreadByRoomIdIn(List.of(room.getId()), 1L);
        anonymousRoomRepository.updateRecipientLastReadMessageId(room.getId(), firstReceivedMessage.getId());
        List<AnonymousRoomUnreadCount> recipientUnreadCounts =
                anonymousMessageRepository.countAllUnreadByRoomIdIn(List.of(room.getId()), 2L);

        assertThat(lastMessages).singleElement().satisfies(message -> {
            assertThat(message.roomId()).isEqualTo(room.getId());
            assertThat(message.messageId()).isEqualTo(lastMessage.getId());
            assertThat(message.content()).isEqualTo("마지막 받은 메시지");
            assertThat(message.senderUserId()).isEqualTo(2L);
            assertThat(message.sentAt()).isNotNull();
        });
        assertThat(unreadCounts).containsExactly(new AnonymousRoomUnreadCount(room.getId(), 2L));
        assertThat(unreadCountsAfterRead).containsExactly(new AnonymousRoomUnreadCount(room.getId(), 1L));
        assertThat(recipientUnreadCounts).isEmpty();
    }

    @Test
    void olderActivity_updateLastActivityAt_doesNotMoveRoomBackward() {
        AnonymousRoom room = persistRoom(10L, 1L, 2L, "익명의 수달");
        LocalDateTime latestActivityAt = LocalDateTime.of(2099, 10, 24, 15, 0);
        updateActivityAt(room, latestActivityAt);

        int updatedCount = anonymousRoomRepository.updateLastActivityAt(
                room.getId(), latestActivityAt.minusMinutes(1));
        entityManager.clear();

        assertThat(updatedCount).isZero();
        assertThat(entityManager.find(AnonymousRoom.class, room.getId()).getLastActivityAt())
                .isEqualTo(latestActivityAt);
    }

    private AnonymousRoom persistRoom(Long productId, Long questionerUserId, Long recipientUserId, String alias) {
        return entityManager.persistAndFlush(
                new AnonymousRoom(productId, questionerUserId, recipientUserId, alias));
    }

    private AnonymousMessage persistMessage(AnonymousRoom room, Long senderUserId, String content) {
        return entityManager.persistAndFlush(
                new AnonymousMessage(room, senderUserId, UUID.randomUUID(), content));
    }

    private void updateActivityAt(AnonymousRoom room, LocalDateTime activityAt) {
        anonymousRoomRepository.updateLastActivityAt(room.getId(), activityAt);
        entityManager.clear();
    }
}
