package kr.ktb.zura.needu.friend.repository;

import java.util.concurrent.atomic.AtomicLong;

import kr.ktb.zura.needu.friend.entity.Friend;
import kr.ktb.zura.needu.user.entity.User;
import kr.ktb.zura.needu.user.type.Gender;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class FriendRepositoryTest {

    private final AtomicLong externalIdSequence = new AtomicLong();

    @Autowired
    private FriendRepository friendRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void friendRelationExists_findActiveFriend_returnsFriendWithFriendUser() {
        User owner = saveUser("나");
        User friendUser = saveUser("친구");
        Friend friend = entityManager.persistAndFlush(new Friend(owner, friendUser));
        entityManager.clear();

        Friend result = friendRepository.findActiveFriend(owner.getId(), friendUser.getId()).orElseThrow();

        assertThat(result.getId()).isEqualTo(friend.getId());
        assertThat(entityManager.getEntityManager().getEntityManagerFactory().getPersistenceUnitUtil()
                .isLoaded(result, "friendUser")).isTrue();
        assertThat(result.getFriendUser().getNickname()).isEqualTo("친구");
    }

    @Test
    void relationOwnedByAnotherUser_findActiveFriend_returnsEmpty() {
        User owner = saveUser("나");
        User other = saveUser("다른 사용자");
        User friendUser = saveUser("친구");
        entityManager.persistAndFlush(new Friend(other, friendUser));
        entityManager.clear();

        assertThat(friendRepository.findActiveFriend(owner.getId(), friendUser.getId())).isEmpty();
    }

    @Test
    void friendUserWithdrawn_findActiveFriend_returnsEmpty() {
        User owner = saveUser("나");
        User friendUser = saveUser("탈퇴한 친구");
        friendUser.withdraw();
        entityManager.persistAndFlush(new Friend(owner, friendUser));
        entityManager.clear();

        assertThat(friendRepository.findActiveFriend(owner.getId(), friendUser.getId())).isEmpty();
    }

    private User saveUser(String nickname) {
        return entityManager.persist(
                new User(externalIdSequence.incrementAndGet(), nickname, null, Gender.NONE, null));
    }
}
