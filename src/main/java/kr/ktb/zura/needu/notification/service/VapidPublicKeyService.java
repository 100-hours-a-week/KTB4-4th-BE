package kr.ktb.zura.needu.notification.service;

import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.notification.dto.response.VapidPublicKeyResponse;
import kr.ktb.zura.needu.notification.exception.NotificationErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class VapidPublicKeyService {

    private final String publicKey;

    public VapidPublicKeyService(@Value("${notification.webpush.public-key:}") String publicKey) {
        this.publicKey = publicKey;
    }

    public VapidPublicKeyResponse findVapidPublicKey() {
        if (publicKey.isBlank()) {
            throw new BusinessException(NotificationErrorCode.NOTIFICATION_VAPID_PUBLIC_KEY_UNAVAILABLE);
        }
        return VapidPublicKeyResponse.from(publicKey);
    }
}
