package kr.ktb.zura.needu.notification.type;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NotificationTypeTest {

    @Test
    void userActionNotifications_getWebPushQueueType_returnsRealtime() {
        assertThat(NotificationType.POKE.getWebPushQueueType()).isEqualTo(WebPushQueueType.REALTIME);
        assertThat(NotificationType.CHAT.getWebPushQueueType()).isEqualTo(WebPushQueueType.REALTIME);
        assertThat(NotificationType.FRIEND_JOINED.getWebPushQueueType()).isEqualTo(WebPushQueueType.REALTIME);
    }

    @Test
    void batchNotifications_getWebPushQueueType_returnsBulk() {
        assertThat(NotificationType.FRIEND_BIRTHDAY.getWebPushQueueType()).isEqualTo(WebPushQueueType.BULK);
        assertThat(NotificationType.PURCHASE_STATUS.getWebPushQueueType()).isEqualTo(WebPushQueueType.BULK);
    }
}
