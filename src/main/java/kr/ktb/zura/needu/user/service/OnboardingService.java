package kr.ktb.zura.needu.user.service;

import kr.ktb.zura.needu.user.dto.request.CompleteOnboardingRequest;
import kr.ktb.zura.needu.user.dto.response.OnboardingStatusResponse;
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

    // TODO: 필수 동의가 없으면 USER_REQUIRED_CONSENT_MISSING, 이미 완료했으면 USER_ONBOARDING_ALREADY_COMPLETED
    //  코드 존재 여부와 NONE 단독 규칙을 검증하고, 성별/생년월일/선물 취향을 저장한 뒤 User.completeOnboarding()을 호출
    @Transactional
    public void completeOnboarding(Long userId, CompleteOnboardingRequest request) {
        throw new UnsupportedOperationException("온보딩 완료 로직 미구현");
    }
}
