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
                .findAllByOwnerUserIdOrderByName(1L, false, "%", Limit.of(10));
        List<FriendSummaryResponse> friendsAfterCursor = friendRepository
                .findAllByOwnerUserIdAfterNameCursor(1L, false, "%", "가나", first.getId(), Limit.of(10));

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
                .findAllByOwnerUserIdOrderByName(1L, true, "%", Limit.of(10));
        List<FriendSummaryResponse> favoritesAfterCursor = friendRepository
                .findAllByOwnerUserIdAfterNameCursor(1L, true, "%", "가가", regular.getId(), Limit.of(10));

        assertThat(favorites).extracting(FriendSummaryResponse::userId)
                .containsExactly(favoriteFirst.getId(), favoriteSecond.getId(), favoriteLater.getId());
        assertThat(favorites).extracting(FriendSummaryResponse::isFavorite).containsOnly(true);
        assertThat(favoritesAfterCursor).extracting(FriendSummaryResponse::userId)
                .containsExactly(favoriteFirst.getId(), favoriteSecond.getId(), favoriteLater.getId());
    }

    @Test
    void inactiveFriends_findAllByOwner_returnsOnlyActiveFriendsOnEveryPage() {
        User active = saveUser("가나", null);
        User withdrawn = saveUser("다라", null);
        withdrawn.withdraw();
        User blockedFavorite = saveUser("마바", null);
        blockedFavorite.block();
        User onboarding = userRepository.save(new User(
                externalIdSequence.incrementAndGet(), "사아", null, Gender.NONE, null));
        friendRepository.saveAll(List.of(
                new Friend(1L, active.getId()),
                new Friend(1L, withdrawn.getId()),
                favoriteFriend(1L, blockedFavorite),
                new Friend(1L, onboarding.getId())
        ));

        List<FriendSummaryResponse> allFriends = friendRepository
                .findAllByOwnerUserIdOrderByName(1L, false, "%", Limit.of(10));
        List<FriendSummaryResponse> friendsAfterCursor = friendRepository
                .findAllByOwnerUserIdAfterNameCursor(1L, false, "%", "가가", active.getId() - 1, Limit.of(10));
        List<FriendSummaryResponse> favorites = friendRepository
                .findAllByOwnerUserIdOrderByName(1L, true, "%", Limit.of(10));

        assertThat(allFriends).extracting(FriendSummaryResponse::userId).containsExactly(active.getId());
        assertThat(friendsAfterCursor).extracting(FriendSummaryResponse::userId).containsExactly(active.getId());
        assertThat(favorites).isEmpty();
    }

    @Test
    void namePrefixPattern_findAllByOwner_returnsNamesStartingWithKeyword() {
        User kimChulsoo = saveUser("김철수", null);
        User kimSuji = saveUser("김수지", null);
        User parkKimji = saveUser("박김지", null);
        User leeYounghee = saveUser("이영희", null);
        friendRepository.saveAll(List.of(
                new Friend(1L, kimChulsoo.getId()),
                new Friend(1L, kimSuji.getId()),
                new Friend(1L, parkKimji.getId()),
                new Friend(1L, leeYounghee.getId())
        ));

        List<FriendSummaryResponse> friends = friendRepository
                .findAllByOwnerUserIdOrderByName(1L, false, "김%", Limit.of(10));
        List<FriendSummaryResponse> friendsAfterCursor = friendRepository
                .findAllByOwnerUserIdAfterNameCursor(1L, false, "김%", "김수지", kimSuji.getId(), Limit.of(10));

        assertThat(friends).extracting(FriendSummaryResponse::userId)
                .containsExactly(kimSuji.getId(), kimChulsoo.getId());
        assertThat(friendsAfterCursor).extracting(FriendSummaryResponse::userId)
                .containsExactly(kimChulsoo.getId());
    }

    @Test
    void escapedWildcardPattern_findAllByOwner_matchesWildcardAsLiteral() {
        User percent = saveUser("50%할인", null);
        User fiftyWon = saveUser("50원", null);
        User underscore = saveUser("a_b", null);
        User letter = saveUser("axb", null);
        friendRepository.saveAll(List.of(
                new Friend(1L, percent.getId()),
                new Friend(1L, fiftyWon.getId()),
                new Friend(1L, underscore.getId()),
                new Friend(1L, letter.getId())
        ));

        assertThat(friendRepository.findAllByOwnerUserIdOrderByName(1L, false, "50!%%", Limit.of(10)))
                .extracting(FriendSummaryResponse::userId).containsExactly(percent.getId());
        assertThat(friendRepository.findAllByOwnerUserIdOrderByName(1L, false, "a!_%", Limit.of(10)))
                .extracting(FriendSummaryResponse::userId).containsExactly(underscore.getId());
    }

    private Friend favoriteFriend(Long ownerUserId, User friendUser) {
        Friend friend = new Friend(ownerUserId, friendUser.getId());
        friend.markAsFavorite();
        return friend;
    }

    private User saveUser(String name, LocalDate birthDate) {
        User user = new User(externalIdSequence.incrementAndGet(), name, null, Gender.NONE, birthDate);
        user.completeOnboarding(Gender.NONE, birthDate);
        return userRepository.save(user);
    }
}
