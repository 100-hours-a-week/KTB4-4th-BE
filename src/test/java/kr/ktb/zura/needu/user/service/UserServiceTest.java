package kr.ktb.zura.needu.user.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.user.dto.response.MyPageResponse;
import kr.ktb.zura.needu.user.dto.response.UserAiProfileResponse;
import kr.ktb.zura.needu.user.dto.response.UserDetailResponse;
import kr.ktb.zura.needu.user.dto.response.UserResponse;
import kr.ktb.zura.needu.user.entity.User;
import kr.ktb.zura.needu.user.entity.UserTasteProfile;
import kr.ktb.zura.needu.user.exception.UserErrorCode;
import kr.ktb.zura.needu.user.repository.UserRepository;
import kr.ktb.zura.needu.user.repository.UserTasteProfileRepository;
import kr.ktb.zura.needu.user.type.AiSummaryStatus;
import kr.ktb.zura.needu.user.type.Gender;
import kr.ktb.zura.needu.user.type.TasteProfileStatus;
import kr.ktb.zura.needu.user.type.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 27, 1, 0);

    private UserRepository userRepository;
    private UserTasteProfileRepository userTasteProfileRepository;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        userTasteProfileRepository = mock(UserTasteProfileRepository.class);
        userService = new UserService(userRepository, userTasteProfileRepository, JsonMapper.builder().build(),
                Clock.fixed(Instant.parse("2026-10-27T01:00:00Z"), ZoneOffset.UTC));
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
        assertFalse(user.onboardingCompleted());
        assertEquals(UserStatus.ONBOARDING, userCaptor.getValue().getStatus());
        assertNotNull(userCaptor.getValue().getLastLoginAt());
    }

    @Test
    void returningUser_reusesExistingUser() {
        User existing = createActiveUser("기존회원");
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
        when(userRepository.findById(1L)).thenReturn(Optional.of(createActiveUser("니듀")));

        assertEquals("니듀", userService.findUserSummary(1L).nickname());
    }

    @Test
    void onboardingUser_validateActiveUser_throwsOnboardingRequired() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(createOnboardingUser()));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> userService.validateActiveUser(1L));

        assertEquals(UserErrorCode.USER_ONBOARDING_REQUIRED, exception.getErrorCode());
    }

    @Test
    void onboardingIncompleteActiveUser_validateActiveUser_throwsOnboardingRequired() {
        User user = new User(42L, "온보딩회원", null, Gender.NONE, null);
        ReflectionTestUtils.setField(user, "status", UserStatus.ACTIVE);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> userService.validateActiveUser(1L));

        assertEquals(UserErrorCode.USER_ONBOARDING_REQUIRED, exception.getErrorCode());
    }

    @Test
    void missingUser_validateActiveUser_throwsUserNotFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> userService.validateActiveUser(1L));

        assertEquals(UserErrorCode.USER_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void activeUser_validateActiveUser_passes() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(createActiveUser("니듀")));

        assertDoesNotThrow(() -> userService.validateActiveUser(1L));
    }

    @Test
    void sameKakaoAccount_validateKakaoIdentity_passes() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(new User(42L, "니듀", null, Gender.NONE, null)));

        assertDoesNotThrow(() -> userService.validateKakaoIdentity(1L, 42L));
    }

    @Test
    void differentKakaoAccount_validateKakaoIdentity_throwsKakaoAccountMismatch() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(new User(42L, "니듀", null, Gender.NONE, null)));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> userService.validateKakaoIdentity(1L, 99L));

        assertEquals(UserErrorCode.USER_KAKAO_ACCOUNT_MISMATCH, exception.getErrorCode());
    }

    @Test
    void userWithoutExternalId_validateKakaoIdentity_throwsKakaoAccountMismatch() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(new User(null, "니듀", null, Gender.NONE, null)));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> userService.validateKakaoIdentity(1L, 99L));

        assertEquals(UserErrorCode.USER_KAKAO_ACCOUNT_MISMATCH, exception.getErrorCode());
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
        User user = createActiveUser("친구", "https://example.com/profile.jpg", LocalDate.of(2000, 2, 29));
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
    void existingUser_completeTasteAnalysis_savesJsonProfileAndMarksCompleted() {
        User user = new User(42L, "니듀", null, Gender.NONE, null);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userTasteProfileRepository.findById(1L)).thenReturn(Optional.empty());

        userService.completeTasteAnalysis(
                1L,
                "캠핑과 핸드드립을 즐기는 분입니다.",
                List.of("취향1", "취향2", "취향3"),
                List.of("캠핑", "자전거타기", "여행"));

        assertTrue(user.isTasteAnalysisCompleted());
        ArgumentCaptor<UserTasteProfile> profileCaptor = ArgumentCaptor.forClass(UserTasteProfile.class);
        verify(userTasteProfileRepository).save(profileCaptor.capture());
        assertEquals("캠핑과 핸드드립을 즐기는 분입니다.", profileCaptor.getValue().getAiSummary());
        assertEquals("[\"취향1\",\"취향2\",\"취향3\"]", profileCaptor.getValue().getRecentTastes());
        assertEquals("[\"캠핑\",\"자전거타기\",\"여행\"]", profileCaptor.getValue().getRecentInterests());
    }

    @Test
    void onboardingUser_completeOnboarding_savesProfileAndTastesAndActivates() {
        User user = new User(42L, "니듀", null, Gender.NONE, null);
        ReflectionTestUtils.setField(user, "status", UserStatus.ONBOARDING);
        ReflectionTestUtils.setField(user, "onboardingCompleted", false);
        Map<String, Object> tastes = Map.of("interestCategoryCodes", List.of("BEAUTY"));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userTasteProfileRepository.findById(1L)).thenReturn(Optional.empty());

        userService.completeOnboarding(1L, Gender.FEMALE, LocalDate.of(2000, 1, 1), tastes);

        assertEquals(Gender.FEMALE, user.getGender());
        assertEquals(LocalDate.of(2000, 1, 1), user.getBirthDate());
        assertTrue(user.isOnboardingCompleted());
        assertEquals(UserStatus.ACTIVE, user.getStatus());
        ArgumentCaptor<UserTasteProfile> profileCaptor = ArgumentCaptor.forClass(UserTasteProfile.class);
        verify(userTasteProfileRepository).save(profileCaptor.capture());
        assertEquals(tastes, profileCaptor.getValue().getOnboardingTastes());
    }

    @Test
    void tasteProfileExists_completeOnboarding_replacesOnboardingTastesOnly() {
        User user = new User(42L, "니듀", null, Gender.NONE, null);
        UserTasteProfile profile = new UserTasteProfile(user, Map.of());
        profile.updateAnalysis("요약", "[\"취향\"]", "[\"관심\"]");
        Map<String, Object> tastes = Map.of("allergyCodes", List.of("MILK"));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userTasteProfileRepository.findById(1L)).thenReturn(Optional.of(profile));

        userService.completeOnboarding(1L, Gender.MALE, LocalDate.of(1999, 12, 31), tastes);

        assertEquals(tastes, profile.getOnboardingTastes());
        assertEquals("요약", profile.getAiSummary());
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

    @Test
    void tasteAnalysisCompleted_findMyPage_returnsKeywordsAndSummary() {
        User user = createActiveUser("니듀", "https://example.com/profile.jpg", LocalDate.of(2000, 1, 1));
        user.completeTasteAnalysis();
        UserTasteProfile profile = new UserTasteProfile(user, Map.of());
        profile.updateAnalysis("홈카페를 좋아해요.", "[\"미니멀\",\"홈카페\"]", "[\"야구\"]");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userTasteProfileRepository.findById(user.getId())).thenReturn(Optional.of(profile));

        MyPageResponse response = userService.findMyPage(1L);

        assertEquals("니듀", response.user().name());
        assertEquals(LocalDate.of(2000, 1, 1), response.user().birthDate());
        assertEquals(TasteProfileStatus.COMPLETED, response.tasteProfile().analysisStatus());
        assertEquals(List.of("미니멀", "홈카페"), response.tasteProfile().preferenceKeywords());
        assertEquals(List.of("야구"), response.tasteProfile().interestKeywords());
        assertEquals(AiSummaryStatus.READY, response.tasteProfile().aiSummary().status());
        assertEquals("홈카페를 좋아해요.", response.tasteProfile().aiSummary().content());
    }

    @Test
    void tasteAnalysisNotStarted_findMyPage_returnsEmptyTasteProfile() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(createActiveUser("니듀")));

        MyPageResponse response = userService.findMyPage(1L);

        assertEquals(TasteProfileStatus.NOT_STARTED, response.tasteProfile().analysisStatus());
        assertTrue(response.tasteProfile().preferenceKeywords().isEmpty());
        assertEquals(AiSummaryStatus.NOT_READY, response.tasteProfile().aiSummary().status());
        assertNull(response.tasteProfile().aiSummary().content());
    }

    @Test
    void onboardingUser_findMyPage_throwsOnboardingRequired() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(createOnboardingUser()));

        BusinessException exception = assertThrows(BusinessException.class, () -> userService.findMyPage(1L));

        assertEquals(UserErrorCode.USER_ONBOARDING_REQUIRED, exception.getErrorCode());
    }

    @Test
    void onboardedUser_findUserAiProfile_returnsUserInfoAndOnboardingTastes() {
        User user = new User(42L, "니듀", null, Gender.NONE, null);
        user.completeOnboarding(Gender.FEMALE, LocalDate.of(2000, 1, 1));
        UserTasteProfile profile = new UserTasteProfile(user, Map.of(
                "interestCategoryCodes", List.of("COFFEE_TEA", "TRAVEL"),
                "allergyCodes", List.of("PEANUT"),
                "giftExclusionCodes", List.of("ALCOHOL")));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userTasteProfileRepository.findById(1L)).thenReturn(Optional.of(profile));

        UserAiProfileResponse response = userService.findUserAiProfile(1L);

        assertEquals(new UserAiProfileResponse(
                "니듀", LocalDate.of(2000, 1, 1), Gender.FEMALE,
                List.of("COFFEE_TEA", "TRAVEL"), List.of("PEANUT"), List.of("ALCOHOL")), response);
    }

    @Test
    void userWithoutTasteProfile_findUserAiProfile_returnsEmptyOnboardingTastes() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(createActiveUser("니듀")));
        when(userTasteProfileRepository.findById(1L)).thenReturn(Optional.empty());

        UserAiProfileResponse response = userService.findUserAiProfile(1L);

        assertEquals("니듀", response.nickname());
        assertTrue(response.interestCategoryCodes().isEmpty());
        assertTrue(response.allergyCodes().isEmpty());
        assertTrue(response.giftExclusionCodes().isEmpty());
    }

    @Test
    void missingUser_findUserAiProfile_throwsUserNotFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> userService.findUserAiProfile(1L));

        assertEquals(UserErrorCode.USER_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void mixedUserStatuses_findAllActiveUsersByIds_returnsOnlyActiveUsers() {
        User activeUser = createActiveUser("친구");
        ReflectionTestUtils.setField(activeUser, "id", 2L);
        User blockedUser = createActiveUser("차단회원");
        ReflectionTestUtils.setField(blockedUser, "id", 3L);
        blockedUser.block();
        when(userRepository.findAllById(List.of(2L, 3L))).thenReturn(List.of(activeUser, blockedUser));

        List<UserDetailResponse> response = userService.findAllActiveUsersByIds(List.of(2L, 3L));

        assertEquals(1, response.size());
        assertEquals(2L, response.getFirst().id());
        assertEquals("친구", response.getFirst().nickname());
    }

    @Test
    void onboardingUser_isOnboardingCompleted_returnsFalseWithoutThrowing() {
        User user = createOnboardingUser();
        ReflectionTestUtils.setField(user, "onboardingCompleted", false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertFalse(userService.isOnboardingCompleted(1L));
    }

    @Test
    void existingUser_withdraw_anonymizesPersonalData() {
        User user = new User(
                42L,
                "니듀",
                "https://example.com/profile.jpg",
                Gender.FEMALE,
                LocalDate.of(2000, 1, 1));
        user.completeOnboarding(Gender.FEMALE, LocalDate.of(2000, 1, 1));
        user.completeTasteAnalysis();
        user.recordLogin();
        user.completeKakaoFriendSync();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        LocalDateTime before = LocalDateTime.now(ZoneOffset.UTC);

        userService.withdraw(1L);

        verify(userTasteProfileRepository).deleteById(1L);
        assertEquals(UserStatus.WITHDRAWN, user.getStatus());
        assertFalse(user.getDeletedAt().isBefore(before));
        assertNull(user.getExternalId());
        assertEquals("탈퇴한 사용자", user.getNickname());
        assertNull(user.getProfileImageUrl());
        assertEquals(Gender.NONE, user.getGender());
        assertNull(user.getBirthDate());
        assertFalse(user.isOnboardingCompleted());
        assertFalse(user.isTasteAnalysisCompleted());
        assertNull(user.getLastLoginAt());
        assertNull(user.getKakaoFriendSyncedAt());
    }

    @Test
    void activeUser_blockUntil_blocksUntilGivenTime() {
        User user = createActiveUser("회원");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        LocalDateTime now = LocalDateTime.of(2026, 10, 27, 1, 0);

        boolean blocked = userService.blockUntil(1L, now, now.plusDays(3));

        assertTrue(blocked);
        assertEquals(UserStatus.BLOCKED, user.getStatus());
        assertEquals(now, user.getBlockedAt());
        assertEquals(now.plusDays(3), user.getBlockedUntil());
    }

    @Test
    void blockedUser_blockUntil_keepsExistingBlock() {
        User user = createActiveUser("회원");
        LocalDateTime firstBlockedAt = LocalDateTime.of(2026, 10, 27, 1, 0);
        user.blockUntil(firstBlockedAt, firstBlockedAt.plusDays(3));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        boolean blocked = userService.blockUntil(1L, firstBlockedAt.plusDays(1), firstBlockedAt.plusDays(4));

        assertFalse(blocked);
        assertEquals(firstBlockedAt, user.getBlockedAt());
        assertEquals(firstBlockedAt.plusDays(3), user.getBlockedUntil());
    }

    @Test
    void expiredBlockUser_blockUntil_blocksAgain() {
        User user = createActiveUser("회원");
        LocalDateTime firstBlockedAt = LocalDateTime.of(2026, 10, 20, 1, 0);
        user.blockUntil(firstBlockedAt, firstBlockedAt.plusDays(3));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        LocalDateTime now = LocalDateTime.of(2026, 10, 27, 1, 0);

        boolean blocked = userService.blockUntil(1L, now, now.plusDays(3));

        assertTrue(blocked);
        assertEquals(now, user.getBlockedAt());
        assertEquals(now.plusDays(3), user.getBlockedUntil());
    }

    @Test
    void withdrawnUser_blockUntil_returnsFalse() {
        User user = createActiveUser("회원");
        user.withdraw();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        LocalDateTime now = LocalDateTime.of(2026, 10, 27, 1, 0);

        boolean blocked = userService.blockUntil(1L, now, now.plusDays(3));

        assertFalse(blocked);
        assertEquals(UserStatus.WITHDRAWN, user.getStatus());
    }

    @Test
    void neverBlockedUser_findLastBlockedAt_returnsEmpty() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(createActiveUser("회원")));

        assertTrue(userService.findLastBlockedAt(1L).isEmpty());
    }

    @Test
    void blockedUserBeforeBlockedUntil_findOrCreateKakaoUser_throwsBlocked() {
        User user = createActiveUser("회원");
        user.blockUntil(NOW.minusDays(1), NOW.plusDays(2));
        when(userRepository.findByExternalId(42L)).thenReturn(Optional.of(user));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> userService.findOrCreateKakaoUser(42L, "회원", null));

        assertEquals(UserErrorCode.USER_BLOCKED, exception.getErrorCode());
        assertEquals(UserStatus.BLOCKED, user.getStatus());
    }

    @Test
    void blockedUserAfterBlockedUntil_findOrCreateKakaoUser_releasesBlock() {
        User user = createActiveUser("회원");
        user.blockUntil(NOW.minusDays(3), NOW);
        when(userRepository.findByExternalId(42L)).thenReturn(Optional.of(user));

        userService.findOrCreateKakaoUser(42L, "회원", null);

        assertEquals(UserStatus.ACTIVE, user.getStatus());
        assertNull(user.getBlockedUntil());
        assertEquals(NOW.minusDays(3), user.getBlockedAt());
    }

    @Test
    void blockedUserWithoutBlockedUntil_findOrCreateKakaoUser_keepsBlock() {
        User user = createActiveUser("회원");
        user.block();
        when(userRepository.findByExternalId(42L)).thenReturn(Optional.of(user));

        assertThrows(BusinessException.class, () -> userService.findOrCreateKakaoUser(42L, "회원", null));
        assertEquals(UserStatus.BLOCKED, user.getStatus());
    }

    private User createOnboardingUser() {
        return new User(42L, "온보딩회원", null, Gender.NONE, null);
    }

    private User createActiveUser(String nickname) {
        return createActiveUser(nickname, null, null);
    }

    private User createActiveUser(String nickname, String profileImageUrl, LocalDate birthDate) {
        User user = new User(42L, nickname, profileImageUrl, Gender.NONE, birthDate);
        user.completeOnboarding(Gender.NONE, birthDate);
        return user;
    }
}
