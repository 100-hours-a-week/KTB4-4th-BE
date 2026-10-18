package kr.ktb.zura.needu.user.service;

import java.util.Map;
import kr.ktb.zura.needu.user.dto.request.UpdateGiftPreferenceRequest;
import kr.ktb.zura.needu.user.dto.response.GiftPreferenceResponse;
import kr.ktb.zura.needu.user.dto.response.GiftPreferenceResultResponse;
import kr.ktb.zura.needu.user.entity.UserTasteProfile;
import kr.ktb.zura.needu.user.repository.UserRepository;
import kr.ktb.zura.needu.user.repository.UserTasteProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GiftPreferenceService {

    private final UserService userService;
    private final UserRepository userRepository;
    private final UserTasteProfileRepository userTasteProfileRepository;

    public GiftPreferenceResponse findGiftPreference(Long userId) {
        userService.validateActiveUser(userId);
        return userTasteProfileRepository.findById(userId)
                .filter(UserTasteProfile::hasOnboardingTastes)
                .map(profile -> GiftPreferenceResponse.from(
                        GiftPreference.fromOnboardingTastes(profile.getOnboardingTastes())))
                .orElseGet(GiftPreferenceResponse::empty);
    }

    @Transactional
    public GiftPreferenceResultResponse updateGiftPreference(Long userId, UpdateGiftPreferenceRequest request) {
        userService.validateActiveUser(userId);
        GiftPreference preference = GiftPreference.from(
                request.interestCategoryCodes(), request.allergyCodes(), request.giftExclusionCodes());

        // V1부터 있던 회원은 취향 프로필 행이 없을 수 있음
        UserTasteProfile profile = userTasteProfileRepository.findById(userId)
                .orElseGet(() -> new UserTasteProfile(userRepository.getReferenceById(userId), Map.of()));
        profile.updateOnboardingTastes(preference.toOnboardingTastes());
        userTasteProfileRepository.save(profile);
        return GiftPreferenceResultResponse.from(preference);
    }
}
