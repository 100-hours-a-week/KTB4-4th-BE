package kr.ktb.zura.needu.notification.repository;

import jakarta.persistence.EntityManager;
import kr.ktb.zura.needu.notification.entity.PushSubscription;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import static org.assertj.core.api.Assertions.assertThat;

// ON DUPLICATE KEY UPDATE는 MySQL 문법이라 integration 프로필의 MySQL을 사용한다.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PushSubscriptionRepositoryIntegrationTest {

    private static final String ENDPOINT = "https://fcm.googleapis.com/fcm/send/abc";
    private static final String ENDPOINT_HASH =
            "4e9bdab8bbe7189c00aebb0922fc49fe40e00594c400f3e1b5dcf97a35c79f85";

    @Autowired
    private PushSubscriptionRepository pushSubscriptionRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void sameEndpoint_upsert_updatesOwnerAndKeys() {
        pushSubscriptionRepository.upsert(1L, ENDPOINT, ENDPOINT_HASH, "old-p256dh", "old-auth");
        pushSubscriptionRepository.upsert(2L, ENDPOINT, ENDPOINT_HASH, "new-p256dh", "new-auth");
        entityManager.clear();

        PushSubscription subscription = pushSubscriptionRepository.findByEndpointHash(ENDPOINT_HASH)
                .orElseThrow();

        assertThat(pushSubscriptionRepository.count()).isOne();
        assertThat(subscription.getUserId()).isEqualTo(2L);
        assertThat(subscription.getEndpoint()).isEqualTo(ENDPOINT);
        assertThat(subscription.getP256dh()).isEqualTo("new-p256dh");
        assertThat(subscription.getAuth()).isEqualTo("new-auth");
    }

    @Test
    void otherUsersEndpoint_deleteByEndpointHashAndUserId_keepsSubscription() {
        pushSubscriptionRepository.upsert(1L, ENDPOINT, ENDPOINT_HASH, "p256dh", "auth");

        pushSubscriptionRepository.deleteByEndpointHashAndUserId(ENDPOINT_HASH, 2L);
        entityManager.flush();
        entityManager.clear();

        assertThat(pushSubscriptionRepository.findByEndpointHash(ENDPOINT_HASH)).isPresent();
    }

    @Test
    void endpointOwnedByAnotherUser_existsByEndpointHashAndUserId_returnsFalseForPreviousOwner() {
        pushSubscriptionRepository.upsert(1L, ENDPOINT, ENDPOINT_HASH, "p256dh", "auth");
        pushSubscriptionRepository.upsert(2L, ENDPOINT, ENDPOINT_HASH, "p256dh", "auth");
        entityManager.clear();

        assertThat(pushSubscriptionRepository.existsByEndpointHashAndUserId(ENDPOINT_HASH, 2L)).isTrue();
        assertThat(pushSubscriptionRepository.existsByEndpointHashAndUserId(ENDPOINT_HASH, 1L)).isFalse();
    }
}
