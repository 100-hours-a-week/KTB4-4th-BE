package kr.ktb.zura.needu.user.service;

import java.time.LocalDate;
import java.util.Optional;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.user.dto.response.UserResponse;
import kr.ktb.zura.needu.user.entity.User;
import kr.ktb.zura.needu.user.exception.UserErrorCode;
import kr.ktb.zura.needu.user.repository.UserRepository;
import kr.ktb.zura.needu.user.type.Gender;
import kr.ktb.zura.needu.user.type.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    @Test
    void withdrawnUser_findOrCreateKakaoUser_throwsUserWithdrawn() {
        User withdrawn = new User(42L, "탈퇴회원", null, Gender.NONE, null);
        withdrawn.withdraw();
        when(userRepository.findByExternalId(42L)).thenReturn(Optional.of(withdrawn));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> userService.findOrCreateKakaoUser(42L, "탈퇴회원", null));

        assertEquals(UserErrorCode.USER_WITHDRAWN, exception.getErrorCode());
    }

    @Test
    void onboardingUser_findOrCreateKakaoUser_allowsLogin() {
        User onboarding = createOnboardingUser();
        when(userRepository.findByExternalId(42L)).thenReturn(Optional.of(onboarding));

        UserResponse user = userService.findOrCreateKakaoUser(42L, "온보딩회원", null);

        assertEquals(42L, user.externalId());
        assertNotNull(onboarding.getLastLoginAt());
    }

    @Test
    void onboardingUser_findUserSummary_throwsOnboardingRequired() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(createOnboardingUser()));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> userService.findUserSummary(1L));

        assertEquals(UserErrorCode.USER_ONBOARDING_REQUIRED, exception.getErrorCode());
    }

    @Test
    void blockedUser_findUserSummary_throwsUserBlocked() {
        User blocked = new User(42L, "차단회원", null, Gender.NONE, null);
        blocked.block();
        when(userRepository.findById(1L)).thenReturn(Optional.of(blocked));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> userService.findUserSummary(1L));

        assertEquals(UserErrorCode.USER_BLOCKED, exception.getErrorCode());
    }

    @Test
    void activeUser_findUserSummary_returnsSummary() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(new User(42L, "니듀", null, Gender.NONE, null)));

        assertEquals("니듀", userService.findUserSummary(1L).nickname());
    }

    @Test
    void authenticatedUser_findAuthenticatedUser_returnsSessionUser() {
        User user = new User(42L, "니듀", null, Gender.NONE, LocalDate.of(2000, 1, 1));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        UserResponse response = userService.findAuthenticatedUser(1L);

        assertEquals("니듀", response.nickname());
        assertEquals(LocalDate.of(2000, 1, 1), response.birthDate());
    }

    @Test
    void activeUser_findUserById_returnsDetails() {
        User user = new User(42L, "친구", "https://example.com/profile.jpg", Gender.NONE, LocalDate.of(2000, 2, 29));
        user.completeTasteAnalysis();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        var response = userService.findUserById(1L).orElseThrow();

        assertEquals("친구", response.nickname());
        assertEquals(user.getProfileImageUrl(), response.profileImageUrl());
        assertEquals(LocalDate.of(2000, 2, 29), response.birthDate());
        assertTrue(response.tasteAnalysisCompleted());
        verify(userRepository).findById(1L);
    }

    @Test
    void missingUser_findUserById_returnsEmpty() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertTrue(userService.findUserById(1L).isEmpty());
    }

    @ParameterizedTest
    @EnumSource(value = UserStatus.class, names = {"ONBOARDING", "BLOCKED", "WITHDRAWN"})
    void inactiveUser_findUserById_returnsEmpty(UserStatus status) {
        User user = new User(42L, "친구", null, Gender.NONE, null);
        ReflectionTestUtils.setField(user, "status", status);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertTrue(userService.findUserById(1L).isEmpty());
    }

    private User createOnboardingUser() {
        User user = new User(42L, "온보딩회원", null, Gender.NONE, null);
        // 현재 User 기본 상태는 ACTIVE이고 ONBOARDING으로 바꾸는 도메인 메서드가 없어 테스트에서만 직접 설정한다.
        ReflectionTestUtils.setField(user, "status", UserStatus.ONBOARDING);
        return user;
    }
}
