package kr.ktb.zura.needu.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.notification.dto.response.VapidPublicKeyResponse;
import kr.ktb.zura.needu.notification.exception.NotificationErrorCode;
import org.junit.jupiter.api.Test;

class VapidPublicKeyServiceTest {

    @Test
    void publicKeyConfigured_findVapidPublicKey_returnsConfiguredKey() {
        VapidPublicKeyService service = new VapidPublicKeyService("test-public-key");

        VapidPublicKeyResponse response = service.findVapidPublicKey();

        assertThat(response.publicKey()).isEqualTo("test-public-key");
    }

    @Test
    void publicKeyMissing_findVapidPublicKey_throwsServiceUnavailable() {
        VapidPublicKeyService service = new VapidPublicKeyService(" ");

        assertThatThrownBy(service::findVapidPublicKey)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(NotificationErrorCode.NOTIFICATION_VAPID_PUBLIC_KEY_UNAVAILABLE);
    }
}
