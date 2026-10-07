package kr.ktb.zura.needu.notification.service;

import kr.ktb.zura.needu.notification.dto.request.UpdateNotificationSettingRequest;
import kr.ktb.zura.needu.notification.dto.response.NotificationSettingResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class NotificationSettingService {

    // TODO: 저장한 적이 없으면 기본값(marketing만 false, updatedAt null)을 내려주도록 구현
    //  기존 알림 설정 조회 API
    public NotificationSettingResponse findNotificationSetting(Long userId) {
        throw new UnsupportedOperationException("기존 알림 설정 조회 로직 미구현");
    }

    // TODO: null이 아닌 필드만 바꾸고 최종값을 반환하도록 구현
    //  알림 설정 업데이트 API
    @Transactional
    public NotificationSettingResponse updateNotificationSetting(
            Long userId, UpdateNotificationSettingRequest request) {
        throw new UnsupportedOperationException("알림 설정 업데이트 로직 미구현");
    }
}
