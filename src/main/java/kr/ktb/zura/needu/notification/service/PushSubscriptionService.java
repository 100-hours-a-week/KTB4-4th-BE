package kr.ktb.zura.needu.notification.service;

import kr.ktb.zura.needu.notification.dto.request.CreatePushSubscriptionRequest;
import kr.ktb.zura.needu.notification.dto.request.DeletePushSubscriptionRequest;
import kr.ktb.zura.needu.notification.dto.response.PushSubscriptionResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PushSubscriptionService {

    // TODO: 허용된 푸시 서비스 도메인만 받도록(SSRF 방지, 아니면 COMMON_INVALID_INPUT)
    //  endpoint 기준 upsert: 새 endpoint는 등록, 내 구독은 keys 갱신, 다른 사용자의 구독은 현재 사용자로 옮기도록
    //  endpoint 전체와 keys는 로그에 남기지 않도록 구현
    //  기기 웹 푸시 구독 API
    @Transactional
    public PushSubscriptionResponse subscribe(Long userId, CreatePushSubscriptionRequest request) {
        throw new UnsupportedOperationException("기기 웹 푸시 구독 로직 미구현");
    }

    // TODO: 내 구독만 지운다. 없거나 다른 사용자의 endpoint여도 예외 없이 끝낸다(멱등)
    //  기기 웹 푸시 구독 해제 API
    @Transactional
    public void unsubscribe(Long userId, DeletePushSubscriptionRequest request) {
        throw new UnsupportedOperationException("기기 웹 푸시 구독 해제 미구현");
    }
}
