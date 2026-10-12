package kr.ktb.zura.needu.friend.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import kr.ktb.zura.needu.friend.entity.Friend;
import kr.ktb.zura.needu.friend.dto.response.FriendSummaryResponse;
import kr.ktb.zura.needu.user.entity.User;
import kr.ktb.zura.needu.user.repository.UserRepository;
import kr.ktb.zura.needu.user.type.Gender;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class FriendRepositoryTest {

    private final AtomicLong externalIdSequence = new AtomicLong();

    @Autowired
    private FriendRepository friendRepository;

    @Autowired
    private UserRepository userRepository;

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

    @Test
    void namesExist_findAllByOwner_ordersByNameAndUsesUserIdAsTieBreaker() {
        User first = saveUser("가나", null);
        User second = saveUser("가나", LocalDate.of(2000, 1, 1));
        User later = saveUser("다라", LocalDate.of(2000, 1, 2));
        User otherOwnerFriend = saveUser("마바", LocalDate.of(2000, 1, 3));
        friendRepository.saveAll(List.of(
                new Friend(1L, later.getId()),
                new Friend(1L, second.getId()),
                new Friend(1L, first.getId()),
                new Friend(2L, otherOwnerFriend.getId())
        ));

        List<FriendSummaryResponse> allFriends = friendRepository
                .findAllByOwnerUserIdOrderByName(1L, false, Limit.of(10));
        List<FriendSummaryResponse> friendsAfterCursor = friendRepository
                .findAllByOwnerUserIdAfterNameCursor(1L, false, "가나", first.getId(), Limit.of(10));

        assertThat(allFriends).extracting(FriendSummaryResponse::userId)
                .containsExactly(first.getId(), second.getId(), later.getId());
        assertThat(friendsAfterCursor).extracting(FriendSummaryResponse::userId)
                .containsExactly(second.getId(), later.getId());
    }

    @Test
    void favoriteOnly_findAllByOwner_returnsOnlyFavoritesByName() {
        User favoriteLater = saveUser("다라", null);
        User regular = saveUser("가가", null);
        User favoriteFirst = saveUser("가나", null);
        User favoriteSecond = saveUser("가나", null);
        User otherOwnerFavorite = saveUser("가", null);
        friendRepository.saveAll(List.of(
                favoriteFriend(1L, favoriteLater),
                new Friend(1L, regular.getId()),
                favoriteFriend(1L, favoriteSecond),
                favoriteFriend(1L, favoriteFirst),
                favoriteFriend(2L, otherOwnerFavorite)
        ));

        List<FriendSummaryResponse> favorites = friendRepository
                .findAllByOwnerUserIdOrderByName(1L, true, Limit.of(10));
        List<FriendSummaryResponse> favoritesAfterCursor = friendRepository
                .findAllByOwnerUserIdAfterNameCursor(1L, true, "가가", regular.getId(), Limit.of(10));

        assertThat(favorites).extracting(FriendSummaryResponse::userId)
                .containsExactly(favoriteFirst.getId(), favoriteSecond.getId(), favoriteLater.getId());
        assertThat(favorites).extracting(FriendSummaryResponse::isFavorite).containsOnly(true);
        assertThat(favoritesAfterCursor).extracting(FriendSummaryResponse::userId)
                .containsExactly(favoriteFirst.getId(), favoriteSecond.getId(), favoriteLater.getId());
    }

    private Friend favoriteFriend(Long ownerUserId, User friendUser) {
        Friend friend = new Friend(ownerUserId, friendUser.getId());
        friend.markAsFavorite();
        return friend;
    }

    private User saveUser(String name, LocalDate birthDate) {
        return userRepository.save(new User(
                externalIdSequence.incrementAndGet(), name, null, Gender.NONE, birthDate));
    }
}
