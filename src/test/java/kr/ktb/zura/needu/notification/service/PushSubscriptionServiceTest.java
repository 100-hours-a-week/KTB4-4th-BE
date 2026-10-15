package kr.ktb.zura.needu.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.Optional;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.CommonErrorCode;
import kr.ktb.zura.needu.notification.dto.request.CreatePushSubscriptionRequest;
import kr.ktb.zura.needu.notification.dto.request.DeletePushSubscriptionRequest;
import kr.ktb.zura.needu.notification.dto.request.PushSubscriptionKeysRequest;
import kr.ktb.zura.needu.notification.dto.response.PushSubscriptionResponse;
import kr.ktb.zura.needu.notification.entity.PushSubscription;
import kr.ktb.zura.needu.notification.repository.PushSubscriptionRepository;
import kr.ktb.zura.needu.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PushSubscriptionServiceTest {

    private static final Long USER_ID = 1L;
    private static final String ENDPOINT = "https://fcm.googleapis.com/fcm/send/abc";
    private static final String ENDPOINT_HASH =
            "4e9bdab8bbe7189c00aebb0922fc49fe40e00594c400f3e1b5dcf97a35c79f85";

    private final PushSubscriptionRepository pushSubscriptionRepository =
            mock(PushSubscriptionRepository.class);
    private final UserService userService = mock(UserService.class);
    private final PushSubscriptionService pushSubscriptionService =
            new PushSubscriptionService(pushSubscriptionRepository, userService);

    @Test
    void allowedEndpoint_subscribe_upsertsSubscription() {
        LocalDateTime updatedAt = LocalDateTime.of(2026, 10, 15, 12, 0);
        PushSubscription subscription = new PushSubscription(
                USER_ID, ENDPOINT, ENDPOINT_HASH, "p256dh", "auth");
        ReflectionTestUtils.setField(subscription, "updatedAt", updatedAt);
        given(pushSubscriptionRepository.findByEndpointHash(ENDPOINT_HASH))
                .willReturn(Optional.of(subscription));

        PushSubscriptionResponse response = pushSubscriptionService.subscribe(
                USER_ID,
                new CreatePushSubscriptionRequest(
                        ENDPOINT,
                        null,
                        new PushSubscriptionKeysRequest("p256dh", "auth")
                )
        );

        verify(userService).validateActiveUser(USER_ID);
        verify(pushSubscriptionRepository).upsert(
                USER_ID, ENDPOINT, ENDPOINT_HASH, "p256dh", "auth");
        assertThat(response).isEqualTo(new PushSubscriptionResponse(true, updatedAt));
    }

    @Test
    void appleEndpoint_subscribe_upsertsSubscription() {
        String endpoint = "https://web.push.apple.com/QPUSH/abc";
        PushSubscription subscription = new PushSubscription(
                USER_ID, endpoint, "hash", "p256dh", "auth");
        given(pushSubscriptionRepository.findByEndpointHash(org.mockito.ArgumentMatchers.anyString()))
                .willReturn(Optional.of(subscription));

        pushSubscriptionService.subscribe(
                USER_ID,
                new CreatePushSubscriptionRequest(
                        endpoint,
                        null,
                        new PushSubscriptionKeysRequest("p256dh", "auth")
                )
        );

        verify(pushSubscriptionRepository).upsert(
                org.mockito.ArgumentMatchers.eq(USER_ID),
                org.mockito.ArgumentMatchers.eq(endpoint),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq("p256dh"),
                org.mockito.ArgumentMatchers.eq("auth")
        );
    }

    @Test
    void untrustedEndpoint_subscribe_throwsInvalidInput() {
        CreatePushSubscriptionRequest request = new CreatePushSubscriptionRequest(
                "https://example.com/push",
                null,
                new PushSubscriptionKeysRequest("p256dh", "auth")
        );

        assertThatThrownBy(() -> pushSubscriptionService.subscribe(USER_ID, request))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(CommonErrorCode.COMMON_INVALID_INPUT));
    }

    @Test
    void suffixSpoofedAppleEndpoint_subscribe_throwsInvalidInput() {
        CreatePushSubscriptionRequest request = new CreatePushSubscriptionRequest(
                "https://evilpush.apple.com/push",
                null,
                new PushSubscriptionKeysRequest("p256dh", "auth")
        );

        assertThatThrownBy(() -> pushSubscriptionService.subscribe(USER_ID, request))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(CommonErrorCode.COMMON_INVALID_INPUT));
    }

    @Test
    void ownedEndpoint_unsubscribe_deletesByHashAndUserId() {
        pushSubscriptionService.unsubscribe(USER_ID, new DeletePushSubscriptionRequest(ENDPOINT));

        verify(userService).validateActiveUser(USER_ID);
        verify(pushSubscriptionRepository).deleteByEndpointHashAndUserId(ENDPOINT_HASH, USER_ID);
    }
}
