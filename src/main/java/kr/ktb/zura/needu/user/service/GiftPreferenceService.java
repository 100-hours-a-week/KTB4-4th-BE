package kr.ktb.zura.needu.user.service;

import kr.ktb.zura.needu.user.dto.request.UpdateGiftPreferenceRequest;
import kr.ktb.zura.needu.user.dto.response.GiftPreferenceResponse;
import kr.ktb.zura.needu.user.dto.response.GiftPreferenceResultResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GiftPreferenceService {

    private final UserService userService;

    // TODO: 온보딩 취향 저장 구조를 정한 뒤 조회
    public GiftPreferenceResponse findGiftPreference(Long userId) {
        userService.validateActiveUser(userId);
        throw new UnsupportedOperationException("온보딩 취향 정보 조회 로직 미구현");
    }

    // TODO: 온보딩 취향 정보 업데이트 로직 구현
    @Transactional
    public GiftPreferenceResultResponse updateGiftPreference(Long userId, UpdateGiftPreferenceRequest request) {
        userService.validateActiveUser(userId);
        throw new UnsupportedOperationException("온보딩 취향 정보 업데이트 로직 미구현");
    }
}
