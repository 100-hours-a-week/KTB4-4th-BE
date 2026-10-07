package kr.ktb.zura.needu.user.service;

import kr.ktb.zura.needu.user.dto.request.UpdateConsentRequest;
import kr.ktb.zura.needu.user.dto.response.ConsentsResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ConsentService {

    // TODO: 동의 항목과 내 동의 이력을 함께 조회
    //  항목 구성은 기획 확정이 필요
    public ConsentsResponse findConsents(Long userId) {
        throw new UnsupportedOperationException("정책 동의 항목과 동의 이력 조회 로직 미구현");
    }

    // TODO: 필수 항목이 false거나 빠졌거나 없는 항목 id면 COMMON_INVALID_INPUT. 현재 버전 기준으로 이력을 남기도록
    @Transactional
    public void updateConsents(Long userId, UpdateConsentRequest request) {
        throw new UnsupportedOperationException("정책 동의 항목 업데이트 로직 미구현");
    }
}
