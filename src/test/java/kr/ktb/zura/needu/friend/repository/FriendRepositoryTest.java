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
    void yearEnd_findAllByOwner_ordersUpcomingBirthdaysAndExcludesMissingBirthday() {
        User today = saveUser("오늘", LocalDate.of(2000, 12, 30));
        User nextYear = saveUser("연초", LocalDate.of(2000, 1, 2));
        User passed = saveUser("지난 생일", LocalDate.of(2000, 12, 20));
        User missing = saveUser("생일 없음", null);
        User otherOwnerFriend = saveUser("다른 사용자 친구", LocalDate.of(2000, 12, 31));
        friendRepository.saveAll(List.of(
                new Friend(1L, today.getId()),
                new Friend(1L, nextYear.getId()),
                new Friend(1L, passed.getId()),
                new Friend(1L, missing.getId()),
                new Friend(2L, otherOwnerFriend.getId())
        ));

        List<FriendSummaryResponse> friends = friendRepository
                .findAllByOwnerUserIdOrderByUpcomingBirthday(1L, 1230, Limit.of(10));

        assertThat(friends).extracting(FriendSummaryResponse::userId)
                .containsExactly(today.getId(), nextYear.getId(), passed.getId());
    }

    @Test
    void leapDay_findAllByOwner_treatsAsMarchFirstAndOrdersByUserId() {
        User leapDay = saveUser("윤년생", LocalDate.of(2000, 2, 29));
        User marchFirst = saveUser("삼월생", LocalDate.of(2000, 3, 1));
        friendRepository.saveAll(List.of(
                new Friend(1L, leapDay.getId()),
                new Friend(1L, marchFirst.getId())
        ));

        List<FriendSummaryResponse> friends = friendRepository
                .findAllByOwnerUserIdOrderByUpcomingBirthday(1L, 201, Limit.of(10));

        assertThat(friends).extracting(FriendSummaryResponse::userId)
                .containsExactly(leapDay.getId(), marchFirst.getId());
        assertThat(friends.getFirst().birthDate()).isEqualTo(LocalDate.of(2000, 2, 29));
    }

    @Test
    void cursorOnSameBirthday_findAllAfterCursor_doesNotSkipFriend() {
        User first = saveUser("첫 번째", LocalDate.of(2000, 5, 1));
        User second = saveUser("두 번째", LocalDate.of(1999, 5, 1));
        User later = saveUser("다음 생일", LocalDate.of(2000, 5, 2));
        friendRepository.saveAll(List.of(
                new Friend(1L, first.getId()),
                new Friend(1L, second.getId()),
                new Friend(1L, later.getId())
        ));

        List<FriendSummaryResponse> friends = friendRepository.findAllByOwnerUserIdAfterBirthdayCursor(
                1L, 401, 501, first.getId(), Limit.of(10));

        assertThat(friends).extracting(FriendSummaryResponse::userId)
                .containsExactly(second.getId(), later.getId());
    }

    private User saveUser(String name, LocalDate birthDate) {
        return userRepository.save(new User(
                externalIdSequence.incrementAndGet(), name, null, Gender.NONE, birthDate));
    }
}
