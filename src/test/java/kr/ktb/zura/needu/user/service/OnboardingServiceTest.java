package kr.ktb.zura.needu.user.service;

import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.user.dto.response.OnboardingStatusResponse;
import kr.ktb.zura.needu.user.exception.UserErrorCode;
import kr.ktb.zura.needu.user.type.OnboardingStep;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OnboardingServiceTest {

    private static final Long USER_ID = 1L;

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
}
