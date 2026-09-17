package kr.ktb.zura.needu.user.service;

import java.util.Optional;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.user.dto.response.UserResponse;
import kr.ktb.zura.needu.user.entity.User;
import kr.ktb.zura.needu.user.repository.UserRepository;
import kr.ktb.zura.needu.user.type.Gender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceTest {

    private UserRepository userRepository;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        userService = new UserService(userRepository);
    }

    @Test
    void firstLogin_createsOnboardingUserWithNoneGender() {
        when(userRepository.findByExternalId(42L)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse user = userService.findOrCreateKakaoUser(42L, null, null);
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        assertEquals(42L, user.externalId());
        assertEquals("사용자", user.nickname());
        assertEquals(Gender.NONE, userCaptor.getValue().getGender());
        assertNotNull(userCaptor.getValue().getLastLoginAt());
    }

    @Test
    void returningUser_reusesExistingUser() {
        User existing = new User(42L, "기존회원", null, Gender.FEMALE, null);
        when(userRepository.findByExternalId(42L)).thenReturn(Optional.of(existing));

        UserResponse user = userService.findOrCreateKakaoUser(42L, "카카오닉네임", null);

        assertEquals("기존회원", user.nickname());
        assertNotNull(existing.getLastLoginAt());
    }

    @Test
    void blockedUser_returnsForbidden() {
        User blocked = new User(42L, "차단회원", null, Gender.NONE, null);
        blocked.block();
        when(userRepository.findByExternalId(42L)).thenReturn(Optional.of(blocked));

        assertThrows(BusinessException.class,
                () -> userService.findOrCreateKakaoUser(42L, "차단회원", null));
    }
}
