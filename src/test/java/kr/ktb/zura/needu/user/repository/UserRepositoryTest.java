package kr.ktb.zura.needu.user.repository;

import java.time.LocalDate;
import java.util.List;
import kr.ktb.zura.needu.user.dto.response.UserSummaryResponse;
import kr.ktb.zura.needu.user.entity.User;
import kr.ktb.zura.needu.user.type.Gender;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.test.context.TestConstructor;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Transactional
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class UserRepositoryTest {

    private final UserRepository userRepository;

    UserRepositoryTest(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Test
    void duplicateExternalId_violatesUniqueConstraint() {
        userRepository.saveAndFlush(new User(42L, "첫 회원", null, Gender.NONE, null));

        assertThrows(DataIntegrityViolationException.class,
                () -> userRepository.saveAndFlush(new User(42L, "다른 회원", null, Gender.NONE, null)));
    }

    @Test
    void withdrawnUser_sameExternalIdCanCreateNewUser() {
        User withdrawn = userRepository.saveAndFlush(new User(42L, "기존 회원", null, Gender.NONE, null));
        withdrawn.withdraw();
        userRepository.flush();

        User rejoined = assertDoesNotThrow(() -> userRepository.saveAndFlush(
                new User(42L, "재가입 회원", null, Gender.NONE, null)));

        assertNotEquals(withdrawn.getId(), rejoined.getId());
    }

    @Test
    void birthdayUsers_findAllActiveByBirthMonthDaysAfterId_returnsActiveUsersAfterCursorInIdOrder() {
        User first = saveActiveUser(1L, "삼월생1", LocalDate.of(2000, 3, 1));
        User leapDay = saveActiveUser(2L, "윤일생", LocalDate.of(2000, 2, 29));
        User second = saveActiveUser(3L, "삼월생2", LocalDate.of(1995, 3, 1));
        User third = saveActiveUser(4L, "삼월생3", LocalDate.of(1990, 3, 1));
        saveActiveUser(5L, "다른날", LocalDate.of(2000, 3, 2));
        saveActiveUser(6L, "탈퇴회원", LocalDate.of(2000, 3, 1)).withdraw();
        userRepository.saveAndFlush(new User(7L, "온보딩중", null, Gender.NONE, LocalDate.of(2000, 3, 1)));

        List<UserSummaryResponse> firstPage =
                userRepository.findAllActiveByBirthMonthDaysAfterId(List.of(301, 229), 0L, Limit.of(2));
        List<UserSummaryResponse> nextPage = userRepository.findAllActiveByBirthMonthDaysAfterId(
                List.of(301, 229), firstPage.getLast().id(), Limit.of(2));

        assertThat(firstPage).extracting(UserSummaryResponse::id).containsExactly(first.getId(), leapDay.getId());
        assertThat(nextPage).extracting(UserSummaryResponse::id).containsExactly(second.getId(), third.getId());
        assertThat(firstPage.getFirst().nickname()).isEqualTo("삼월생1");
        assertThat(userRepository.findAllActiveByBirthMonthDaysAfterId(List.of(301), third.getId(), Limit.of(2)))
                .isEmpty();
    }

    private User saveActiveUser(Long externalId, String nickname, LocalDate birthDate) {
        User user = new User(externalId, nickname, null, Gender.NONE, birthDate);
        user.completeOnboarding(Gender.NONE, birthDate);
        return userRepository.saveAndFlush(user);
    }
}
