package kr.ktb.zura.needu.friend.repository;

import kr.ktb.zura.needu.friend.entity.Friend;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class FriendRepositoryTest {

    @Autowired
    private FriendRepository friendRepository;

    @Test
    void friendRelationExists_existsByOwnerAndFriend_returnsTrue() {
        friendRepository.saveAndFlush(new Friend(1L, 2L));

        assertThat(friendRepository.existsByOwnerUserIdAndFriendUserId(1L, 2L)).isTrue();
        assertThat(friendRepository.existsByOwnerUserIdAndFriendUserId(3L, 2L)).isFalse();
    }

    @Test
    void duplicateOwnerAndFriend_save_violatesUniqueConstraint() {
        friendRepository.saveAndFlush(new Friend(1L, 2L));

        assertThatThrownBy(() -> friendRepository.saveAndFlush(new Friend(1L, 2L)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
