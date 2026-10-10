package kr.ktb.zura.needu.user.service;

import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.user.dto.request.CompleteOnboardingRequest;
import kr.ktb.zura.needu.user.dto.response.OnboardingStatusResponse;
import kr.ktb.zura.needu.user.exception.UserErrorCode;
import kr.ktb.zura.needu.user.type.OnboardingStep;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OnboardingService {

    private final UserService userService;
    private final ConsentService consentService;

    public OnboardingStatusResponse findOnboardingStatus(Long userId) {
        if (userService.isOnboardingCompleted(userId)) {
            return OnboardingStatusResponse.completedStatus();
        }

        OnboardingStep currentStep = consentService.hasAgreedToRequiredConsents(userId)
                ? OnboardingStep.PROFILE
                : OnboardingStep.CONSENTS;
        return OnboardingStatusResponse.inProgressStatus(currentStep);
    }

    @Transactional
    public void completeOnboarding(Long userId, CompleteOnboardingRequest request) {
        if (userService.isOnboardingCompleted(userId)) {
            throw new BusinessException(UserErrorCode.USER_ONBOARDING_ALREADY_COMPLETED);
        }
        if (!consentService.hasAgreedToRequiredConsents(userId)) {
            throw new BusinessException(UserErrorCode.USER_REQUIRED_CONSENT_MISSING);
        }
        GiftPreference giftPreference = GiftPreference.from(
                request.interestCategoryCodes(), request.allergyCodes(), request.giftExclusionCodes());
        userService.completeOnboarding(
                userId, request.gender(), request.birthDate(), giftPreference.toOnboardingTastes());
    }
}
