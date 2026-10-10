package kr.ktb.zura.needu.user.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.CommonErrorCode;
import kr.ktb.zura.needu.user.dto.request.CompleteOnboardingRequest;
import kr.ktb.zura.needu.user.dto.response.OnboardingStatusResponse;
import kr.ktb.zura.needu.user.exception.UserErrorCode;
import kr.ktb.zura.needu.user.type.Gender;
import kr.ktb.zura.needu.user.type.OnboardingStep;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OnboardingServiceTest {

    private static final Long USER_ID = 1L;
    private static final LocalDate BIRTH_DATE = LocalDate.of(2000, 1, 1);

    private UserService userService;
    private ConsentService consentService;
    private OnboardingService onboardingService;

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        consentService = mock(ConsentService.class);
        onboardingService = new OnboardingService(userService, consentService);
    }

    @Test
    void onboardingCompleted_findOnboardingStatus_returnsCompletedWithoutStep() {
        when(userService.isOnboardingCompleted(USER_ID)).thenReturn(true);

        OnboardingStatusResponse response = onboardingService.findOnboardingStatus(USER_ID);

        assertThat(response.completed()).isTrue();
        assertThat(response.currentStep()).isNull();
        verify(consentService, never()).hasAgreedToRequiredConsents(any());
    }

    @Test
    void requiredConsentsNotAgreed_findOnboardingStatus_returnsConsentsStep() {
        when(userService.isOnboardingCompleted(USER_ID)).thenReturn(false);
        when(consentService.hasAgreedToRequiredConsents(USER_ID)).thenReturn(false);

        OnboardingStatusResponse response = onboardingService.findOnboardingStatus(USER_ID);

        assertThat(response.completed()).isFalse();
        assertThat(response.currentStep()).isEqualTo(OnboardingStep.CONSENTS);
    }

    @Test
    void requiredConsentsAgreed_findOnboardingStatus_returnsProfileStep() {
        when(userService.isOnboardingCompleted(USER_ID)).thenReturn(false);
        when(consentService.hasAgreedToRequiredConsents(USER_ID)).thenReturn(true);

        OnboardingStatusResponse response = onboardingService.findOnboardingStatus(USER_ID);

        assertThat(response.completed()).isFalse();
        assertThat(response.currentStep()).isEqualTo(OnboardingStep.PROFILE);
    }

    @Test
    void blockedUser_findOnboardingStatus_throwsUserBlocked() {
        when(userService.isOnboardingCompleted(USER_ID))
                .thenThrow(new BusinessException(UserErrorCode.USER_BLOCKED));

        assertThatThrownBy(() -> onboardingService.findOnboardingStatus(USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.USER_BLOCKED);
        verify(consentService, never()).hasAgreedToRequiredConsents(any());
    }

    @Test
    void consentedOnboardingUser_completeOnboarding_savesProfileAndTastes() {
        when(userService.isOnboardingCompleted(USER_ID)).thenReturn(false);
        when(consentService.hasAgreedToRequiredConsents(USER_ID)).thenReturn(true);

        onboardingService.completeOnboarding(USER_ID, validRequest());

        verify(userService).completeOnboarding(USER_ID, Gender.MALE, BIRTH_DATE, Map.of(
                "interestCategoryCodes", List.of("FASHION", "BEAUTY"),
                "allergyCodes", List.of("PEANUT"),
                "giftExclusionCodes", List.of("PERFUME")));
    }

    @Test
    void onboardingCompleted_completeOnboarding_throwsAlreadyCompleted() {
        when(userService.isOnboardingCompleted(USER_ID)).thenReturn(true);

        assertErrorCode(validRequest(), UserErrorCode.USER_ONBOARDING_ALREADY_COMPLETED);
        verify(consentService, never()).hasAgreedToRequiredConsents(any());
    }

    @Test
    void requiredConsentsNotAgreed_completeOnboarding_throwsRequiredConsentMissing() {
        when(userService.isOnboardingCompleted(USER_ID)).thenReturn(false);
        when(consentService.hasAgreedToRequiredConsents(USER_ID)).thenReturn(false);

        assertErrorCode(validRequest(), UserErrorCode.USER_REQUIRED_CONSENT_MISSING);
    }

    @Test
    void unknownCode_completeOnboarding_throwsInvalidInputWithoutSaving() {
        when(userService.isOnboardingCompleted(USER_ID)).thenReturn(false);
        when(consentService.hasAgreedToRequiredConsents(USER_ID)).thenReturn(true);
        CompleteOnboardingRequest request = new CompleteOnboardingRequest(
                Gender.MALE, BIRTH_DATE, List.of("FASHION"), List.of("POLLEN"), List.of());

        assertErrorCode(request, CommonErrorCode.COMMON_INVALID_INPUT);
    }

    private void assertErrorCode(CompleteOnboardingRequest request, Object errorCode) {
        assertThatThrownBy(() -> onboardingService.completeOnboarding(USER_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(errorCode);
        verify(userService, never()).completeOnboarding(anyLong(), any(), any(), any());
    }

    private static CompleteOnboardingRequest validRequest() {
        return new CompleteOnboardingRequest(
                Gender.MALE, BIRTH_DATE, List.of("FASHION", "BEAUTY"), List.of("PEANUT"), List.of("PERFUME"));
    }
}
