package kr.ktb.zura.needu.notification.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PushSubscriptionTest {

    @Test
    void updateSubscription_changesOwnerAndKeys() {
        PushSubscription subscription = new PushSubscription(1L, "endpoint", "old-p256dh", "old-auth");

        subscription.updateSubscription(2L, "new-p256dh", "new-auth");

        assertThat(subscription.isOwnedBy(1L)).isFalse();
        assertThat(subscription.isOwnedBy(2L)).isTrue();
        assertThat(subscription.getEndpoint()).isEqualTo("endpoint");
        assertThat(subscription.getP256dh()).isEqualTo("new-p256dh");
        assertThat(subscription.getAuth()).isEqualTo("new-auth");
    }
}
