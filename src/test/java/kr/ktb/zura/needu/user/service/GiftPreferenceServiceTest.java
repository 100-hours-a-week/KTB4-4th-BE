package kr.ktb.zura.needu.user.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.CommonErrorCode;
import kr.ktb.zura.needu.user.dto.request.UpdateGiftPreferenceRequest;
import kr.ktb.zura.needu.user.dto.response.GiftPreferenceResponse;
import kr.ktb.zura.needu.user.dto.response.GiftPreferenceResultResponse;
import kr.ktb.zura.needu.user.entity.User;
import kr.ktb.zura.needu.user.entity.UserTasteProfile;
import kr.ktb.zura.needu.user.exception.UserErrorCode;
import kr.ktb.zura.needu.user.repository.UserRepository;
import kr.ktb.zura.needu.user.repository.UserTasteProfileRepository;
import kr.ktb.zura.needu.user.type.Gender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GiftPreferenceServiceTest {

    private static final Long USER_ID = 1L;

    private UserService userService;
    private UserRepository userRepository;
    private UserTasteProfileRepository userTasteProfileRepository;
    private GiftPreferenceService giftPreferenceService;
    private User user;

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        userRepository = mock(UserRepository.class);
        userTasteProfileRepository = mock(UserTasteProfileRepository.class);
        giftPreferenceService = new GiftPreferenceService(userService, userRepository, userTasteProfileRepository);
        user = new User(42L, "니듀", null, Gender.NONE, null);
    }

    @Test
    void noTasteProfile_findGiftPreference_returnsEmpty() {
        when(userTasteProfileRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertEmpty(giftPreferenceService.findGiftPreference(USER_ID));
    }

    @Test
    void analysisOnlyProfile_findGiftPreference_returnsEmpty() {
        when(userTasteProfileRepository.findById(USER_ID))
                .thenReturn(Optional.of(new UserTasteProfile(user, Map.of())));

        assertEmpty(giftPreferenceService.findGiftPreference(USER_ID));
    }

    @Test
    void onboardingTastesSaved_findGiftPreference_returnsCodes() {
        UserTasteProfile profile = new UserTasteProfile(user, Map.of(
                "interestCategoryCodes", List.of("BEAUTY", "HOME_INTERIOR"),
                "allergyCodes", List.of("NUTS"),
                "giftExclusionCodes", List.of("PERFUME")));
        when(userTasteProfileRepository.findById(USER_ID)).thenReturn(Optional.of(profile));

        GiftPreferenceResponse response = giftPreferenceService.findGiftPreference(USER_ID);

        assertThat(response.exists()).isTrue();
        assertThat(response.interestCategoryCodes()).containsExactly("BEAUTY", "HOME_INTERIOR");
        assertThat(response.allergyCodes()).containsExactly("NUTS");
        assertThat(response.giftExclusionCodes()).containsExactly("PERFUME");
    }

    @Test
    void onboardingRequiredUser_findGiftPreference_throwsOnboardingRequired() {
        doThrow(new BusinessException(UserErrorCode.USER_ONBOARDING_REQUIRED))
                .when(userService).validateActiveUser(USER_ID);

        assertThatThrownBy(() -> giftPreferenceService.findGiftPreference(USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.USER_ONBOARDING_REQUIRED);
    }

    @Test
    void tasteProfileExists_updateGiftPreference_replacesTastesAndReturnsResult() {
        UserTasteProfile profile = new UserTasteProfile(user, Map.of("allergyCodes", List.of("MILK")));
        when(userTasteProfileRepository.findById(USER_ID)).thenReturn(Optional.of(profile));

        GiftPreferenceResultResponse response = giftPreferenceService.updateGiftPreference(USER_ID, validRequest());

        assertThat(profile.getOnboardingTastes()).isEqualTo(Map.of(
                "interestCategoryCodes", List.of("FASHION"),
                "allergyCodes", List.of(),
                "giftExclusionCodes", List.of("ALCOHOL", "CLOTHING")));
        assertThat(response.interestCategoryCodes()).containsExactly("FASHION");
        assertThat(response.allergyCodes()).isEmpty();
        assertThat(response.giftExclusionCodes()).containsExactly("ALCOHOL", "CLOTHING");
        verify(userTasteProfileRepository).save(profile);
    }

    @Test
    void noTasteProfile_updateGiftPreference_createsProfile() {
        when(userTasteProfileRepository.findById(USER_ID)).thenReturn(Optional.empty());
        when(userRepository.getReferenceById(USER_ID)).thenReturn(user);

        giftPreferenceService.updateGiftPreference(USER_ID, validRequest());

        ArgumentCaptor<UserTasteProfile> captor = ArgumentCaptor.forClass(UserTasteProfile.class);
        verify(userTasteProfileRepository).save(captor.capture());
        assertThat(captor.getValue().getUser()).isSameAs(user);
        assertThat(captor.getValue().hasOnboardingTastes()).isTrue();
    }

    @Test
    void unknownCode_updateGiftPreference_throwsInvalidInputWithoutSaving() {
        UpdateGiftPreferenceRequest request = new UpdateGiftPreferenceRequest(
                List.of("LIVING"), List.of(), List.of());

        assertThatThrownBy(() -> giftPreferenceService.updateGiftPreference(USER_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.COMMON_INVALID_INPUT);
        verify(userTasteProfileRepository, never()).save(any());
    }

    @Test
    void blockedUser_updateGiftPreference_throwsUserBlocked() {
        doThrow(new BusinessException(UserErrorCode.USER_BLOCKED))
                .when(userService).validateActiveUser(USER_ID);

        assertThatThrownBy(() -> giftPreferenceService.updateGiftPreference(USER_ID, validRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.USER_BLOCKED);
        verify(userTasteProfileRepository, never()).save(any());
    }

    private static UpdateGiftPreferenceRequest validRequest() {
        return new UpdateGiftPreferenceRequest(List.of("FASHION"), List.of(), List.of("ALCOHOL", "CLOTHING"));
    }

    private static void assertEmpty(GiftPreferenceResponse response) {
        assertThat(response.exists()).isFalse();
        assertThat(response.interestCategoryCodes()).isEmpty();
        assertThat(response.allergyCodes()).isEmpty();
        assertThat(response.giftExclusionCodes()).isEmpty();
    }
}
