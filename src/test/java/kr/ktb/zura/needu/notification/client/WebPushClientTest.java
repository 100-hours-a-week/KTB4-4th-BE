package kr.ktb.zura.needu.notification.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.zerodeplibs.webpush.VAPIDKeyPair;
import com.zerodeplibs.webpush.VAPIDKeyPairs;
import com.zerodeplibs.webpush.key.PrivateKeySources;
import com.zerodeplibs.webpush.key.PublicKeySources;
import java.math.BigInteger;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Base64;
import kr.ktb.zura.needu.notification.config.WebPushProperties;
import kr.ktb.zura.needu.notification.entity.PushSubscription;
import kr.ktb.zura.needu.notification.service.NotificationsCreatedEvent;
import kr.ktb.zura.needu.notification.type.NotificationCategory;
import kr.ktb.zura.needu.notification.type.NotificationResourceType;
import kr.ktb.zura.needu.notification.type.NotificationType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

class WebPushClientTest {

    @Test
    void validSubscription_send_buildsEncryptedPostRequestAndReturnsSuccess() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<Void> response = mock(HttpResponse.class);
        given(response.statusCode()).willReturn(201);
        given(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .willReturn(response);
        WebPushClient webPushClient = new WebPushClient(
                httpClient,
                vapidKeyPair(),
                new WebPushProperties(
                        "unused-public-key",
                        "unused-private-key",
                        "mailto:needu@example.com",
                        Duration.ofHours(24),
                        Duration.ofSeconds(5)
                ),
                JsonMapper.builder().build()
        );

        WebPushResult result = webPushClient.send(subscription(), notification());

        assertThat(result).isEqualTo(WebPushResult.SUCCESS);
        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));
        HttpRequest request = requestCaptor.getValue();
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.uri().toString()).isEqualTo("https://fcm.googleapis.com/fcm/send/test-subscription");
        assertThat(request.bodyPublisher()).isPresent();
        assertThat(request.headers().firstValue("Authorization")).isPresent();
        assertThat(request.headers().firstValue("Content-Encoding")).contains("aes128gcm");
    }

    private VAPIDKeyPair vapidKeyPair() throws Exception {
        KeyPair keyPair = generateKeyPair();
        return VAPIDKeyPairs.of(
                PrivateKeySources.ofECPrivateKey((ECPrivateKey) keyPair.getPrivate()),
                PublicKeySources.ofECPublicKey((ECPublicKey) keyPair.getPublic())
        );
    }

    private PushSubscription subscription() throws Exception {
        ECPublicKey publicKey = (ECPublicKey) generateKeyPair().getPublic();
        String p256dh = Base64.getUrlEncoder().withoutPadding().encodeToString(toUncompressedBytes(publicKey));
        String auth = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[16]);
        return new PushSubscription(
                1L,
                "https://fcm.googleapis.com/fcm/send/test-subscription",
                "endpoint-hash",
                p256dh,
                auth
        );
    }

    private KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        return generator.generateKeyPair();
    }

    private byte[] toUncompressedBytes(ECPublicKey publicKey) {
        byte[] result = new byte[65];
        result[0] = 4;
        copyCoordinate(publicKey.getW().getAffineX(), result, 1);
        copyCoordinate(publicKey.getW().getAffineY(), result, 33);
        return result;
    }

    private void copyCoordinate(BigInteger coordinate, byte[] target, int offset) {
        byte[] bytes = coordinate.toByteArray();
        int sourceOffset = Math.max(0, bytes.length - 32);
        int length = Math.min(bytes.length, 32);
        Arrays.fill(target, offset, offset + 32, (byte) 0);
        System.arraycopy(bytes, sourceOffset, target, offset + 32 - length, length);
    }

    private NotificationsCreatedEvent.CreatedNotification notification() {
        return new NotificationsCreatedEvent.CreatedNotification(
                10L,
                1L,
                NotificationCategory.POKE,
                NotificationType.POKE,
                "친구가 콕 찔렀어요.",
                "친구의 알림을 확인해 보세요.",
                NotificationResourceType.USER,
                2L,
                LocalDateTime.of(2026, 10, 15, 12, 0)
        );
    }
}
