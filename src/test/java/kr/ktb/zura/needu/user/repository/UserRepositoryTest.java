package kr.ktb.zura.needu.user.repository;

import kr.ktb.zura.needu.user.entity.User;
import kr.ktb.zura.needu.user.type.Gender;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestConstructor;
import org.springframework.transaction.annotation.Transactional;

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
}
