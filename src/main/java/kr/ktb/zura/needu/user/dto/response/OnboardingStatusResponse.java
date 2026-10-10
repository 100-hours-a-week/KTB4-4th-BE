package kr.ktb.zura.needu.user.dto.response;

import kr.ktb.zura.needu.user.type.OnboardingStep;

public record OnboardingStatusResponse(boolean completed, OnboardingStep currentStep) {

    public static OnboardingStatusResponse completedStatus() {
        return new OnboardingStatusResponse(true, null);
    }

    public static OnboardingStatusResponse inProgressStatus(OnboardingStep currentStep) {
        return new OnboardingStatusResponse(false, currentStep);
    }
}
