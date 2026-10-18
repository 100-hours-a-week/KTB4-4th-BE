package kr.ktb.zura.needu.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import kr.ktb.zura.needu.notification.entity.Notification;
import kr.ktb.zura.needu.notification.entity.PushSubscription;
import kr.ktb.zura.needu.notification.repository.NotificationEventRepository;
import kr.ktb.zura.needu.notification.repository.NotificationRepository;
import kr.ktb.zura.needu.notification.repository.PushSubscriptionRepository;
import kr.ktb.zura.needu.notification.service.NotificationCreationService;
import kr.ktb.zura.needu.notification.type.NotificationResourceType;
import kr.ktb.zura.needu.notification.type.NotificationType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.json.JsonMapper;

// 로컬 HTTP 서버를 푸시 서비스로 사용해 알림 생성 → 커밋 후 비동기 발송 → 암호화 본문까지 실제 경로로 검증한다.
@SpringBootTest
class WebPushDeliveryIntegrationTest {

    private static final Duration DELIVERY_TIMEOUT = Duration.ofSeconds(10);
    private static final long NOTIFICATION_RETENTION_DAYS = 7;
    private static final int AUTH_SECRET_LENGTH = 16;
    private static final int SALT_LENGTH = 16;
    private static final int RECORD_SIZE_LENGTH = 4;
    private static final int GCM_TAG_BITS = 128;
    private static final int CONTENT_ENCRYPTION_KEY_LENGTH = 16;
    private static final int NONCE_LENGTH = 12;
    private static final byte LAST_RECORD_DELIMITER = 0x02;
    private static final String PUSH_PATH = "/push/";

    private static final KeyPair VAPID_KEY_PAIR = WebPushTestKeys.generateKeyPair();
    private static final BlockingQueue<CapturedPushRequest> RECEIVED_REQUESTS = new LinkedBlockingQueue<>();
    private static final AtomicInteger PUSH_SERVICE_STATUS = new AtomicInteger(201);

    private static HttpServer pushService;

    @Autowired
    private NotificationCreationService notificationCreationService;

    @Autowired
    private PushSubscriptionRepository pushSubscriptionRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationEventRepository notificationEventRepository;

    @Autowired
    private JsonMapper jsonMapper;

    private final KeyPair subscriberKeyPair = WebPushTestKeys.generateKeyPair();
    private final byte[] subscriberAuthSecret = new byte[AUTH_SECRET_LENGTH];

    @DynamicPropertySource
    static void webPushProperties(DynamicPropertyRegistry registry) {
        registry.add("notification.webpush.enabled", () -> "true");
        registry.add("notification.webpush.public-key", () -> WebPushTestKeys.toPublicKeyProperty(VAPID_KEY_PAIR));
        registry.add("notification.webpush.private-key", () -> WebPushTestKeys.toPrivateKeyProperty(VAPID_KEY_PAIR));
        registry.add("notification.webpush.subject", () -> "mailto:needu@example.com");
        registry.add("notification.webpush.ttl", () -> "24h");
        registry.add("notification.webpush.request-timeout", () -> "5s");
    }

    @BeforeAll
    static void startPushService() throws IOException {
        pushService = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        pushService.createContext(PUSH_PATH, exchange -> {
            RECEIVED_REQUESTS.add(new CapturedPushRequest(
                    exchange.getRequestMethod(),
                    exchange.getRequestURI().getPath(),
                    Map.of(
                            "TTL", String.valueOf(exchange.getRequestHeaders().getFirst("TTL")),
                            "Content-Encoding", String.valueOf(exchange.getRequestHeaders().getFirst("Content-Encoding")),
                            "Authorization", String.valueOf(exchange.getRequestHeaders().getFirst("Authorization"))
                    ),
                    exchange.getRequestBody().readAllBytes()
            ));
            exchange.sendResponseHeaders(PUSH_SERVICE_STATUS.get(), -1);
            exchange.close();
        });
        pushService.start();
    }

    @AfterAll
    static void stopPushService() {
        pushService.stop(0);
    }

    @BeforeEach
    void setUp() {
        RECEIVED_REQUESTS.clear();
        PUSH_SERVICE_STATUS.set(201);
        Arrays.fill(subscriberAuthSecret, (byte) 7);
    }

    @AfterEach
    void tearDown() {
        notificationRepository.deleteAll();
        notificationEventRepository.deleteAll();
        pushSubscriptionRepository.deleteAll();
    }

    @Test
    void notificationCreated_sendsEncryptedPayloadToReceiverSubscriptionOnly() throws Exception {
        Long receiverUserId = 501L;
        Long otherUserId = 502L;
        saveSubscription(receiverUserId, "receiver");
        saveSubscription(otherUserId, "other");

        List<Notification> notifications = notificationCreationService.createNotifications(
                Set.of(receiverUserId),
                NotificationType.POKE,
                otherUserId,
                "친구가 콕 찔렀어요.",
                "친구의 알림을 확인해 보세요.",
                NotificationResourceType.USER,
                otherUserId,
                null,
                LocalDateTime.now().plusDays(NOTIFICATION_RETENTION_DAYS)
        );

        CapturedPushRequest request = RECEIVED_REQUESTS.poll(DELIVERY_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.path()).isEqualTo(PUSH_PATH + "receiver");
        assertThat(request.headers().get("TTL")).isEqualTo("86400");
        assertThat(request.headers().get("Content-Encoding")).isEqualTo("aes128gcm");
        assertThat(request.headers().get("Authorization"))
                .startsWith("vapid ")
                .contains("k=" + WebPushTestKeys.toPublicKeyProperty(VAPID_KEY_PAIR));

        Map<String, Object> payload = jsonMapper.readValue(decrypt(request.body()), Map.class);
        assertThat(payload).containsOnlyKeys(
                "notificationId", "category", "title", "body", "resourceType", "resourceId");
        assertThat(((Number) payload.get("notificationId")).longValue())
                .isEqualTo(notifications.getFirst().getId());
        assertThat(payload.get("category")).isEqualTo("POKE");
        assertThat(payload.get("title")).isEqualTo("친구가 콕 찔렀어요.");
        assertThat(payload.get("body")).isEqualTo("친구의 알림을 확인해 보세요.");
        assertThat(payload.get("resourceType")).isEqualTo("USER");
        assertThat(((Number) payload.get("resourceId")).longValue()).isEqualTo(otherUserId);

        assertThat(RECEIVED_REQUESTS.poll(1, TimeUnit.SECONDS)).isNull();
    }

    @Test
    void pushServiceRespondsGone_deletesExpiredSubscription() throws Exception {
        Long receiverUserId = 503L;
        PushSubscription subscription = saveSubscription(receiverUserId, "expired");
        PUSH_SERVICE_STATUS.set(410);

        notificationCreationService.createNotifications(
                Set.of(receiverUserId),
                NotificationType.POKE,
                null,
                "친구가 콕 찔렀어요.",
                "친구의 알림을 확인해 보세요.",
                null,
                null,
                null,
                LocalDateTime.now().plusDays(NOTIFICATION_RETENTION_DAYS)
        );

        assertThat(RECEIVED_REQUESTS.poll(DELIVERY_TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isNotNull();
        assertThat(waitUntilDeleted(subscription.getId())).isTrue();
    }

    private PushSubscription saveSubscription(Long userId, String token) {
        String endpoint = "http://localhost:" + pushService.getAddress().getPort() + PUSH_PATH + token;
        return pushSubscriptionRepository.save(new PushSubscription(
                userId,
                endpoint,
                "endpoint-hash-" + token,
                WebPushTestKeys.toBase64Url(
                        WebPushTestKeys.toUncompressedBytes((ECPublicKey) subscriberKeyPair.getPublic())),
                WebPushTestKeys.toBase64Url(subscriberAuthSecret)
        ));
    }

    private boolean waitUntilDeleted(Long subscriptionId) throws InterruptedException {
        Instant deadline = Instant.now().plus(DELIVERY_TIMEOUT);
        while (Instant.now().isBefore(deadline)) {
            if (!pushSubscriptionRepository.existsById(subscriptionId)) {
                return true;
            }
            Thread.sleep(50);
        }
        return false;
    }

    // RFC 8291(aes128gcm)에 따라 브라우저가 하는 복호화를 그대로 수행한다.
    private String decrypt(byte[] body) throws GeneralSecurityException {
        ByteBuffer buffer = ByteBuffer.wrap(body);
        byte[] salt = new byte[SALT_LENGTH];
        buffer.get(salt);
        buffer.position(buffer.position() + RECORD_SIZE_LENGTH);
        byte[] serverPublicKeyBytes = new byte[Byte.toUnsignedInt(buffer.get())];
        buffer.get(serverPublicKeyBytes);
        byte[] ciphertext = new byte[buffer.remaining()];
        buffer.get(ciphertext);

        ECPublicKey subscriberPublicKey = (ECPublicKey) subscriberKeyPair.getPublic();
        KeyAgreement keyAgreement = KeyAgreement.getInstance("ECDH");
        keyAgreement.init(subscriberKeyPair.getPrivate());
        keyAgreement.doPhase(toPublicKey(serverPublicKeyBytes, subscriberPublicKey), true);
        byte[] sharedSecret = keyAgreement.generateSecret();

        byte[] keyInfo = concat(
                "WebPush: info".getBytes(StandardCharsets.US_ASCII),
                new byte[] {0},
                WebPushTestKeys.toUncompressedBytes(subscriberPublicKey),
                serverPublicKeyBytes,
                new byte[] {1}
        );
        byte[] inputKeyingMaterial = hmac(hmac(subscriberAuthSecret, sharedSecret), keyInfo);
        byte[] pseudoRandomKey = hmac(salt, inputKeyingMaterial);
        byte[] contentEncryptionKey = Arrays.copyOf(
                hmac(pseudoRandomKey, info("Content-Encoding: aes128gcm")), CONTENT_ENCRYPTION_KEY_LENGTH);
        byte[] nonce = Arrays.copyOf(hmac(pseudoRandomKey, info("Content-Encoding: nonce")), NONCE_LENGTH);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(
                Cipher.DECRYPT_MODE,
                new SecretKeySpec(contentEncryptionKey, "AES"),
                new GCMParameterSpec(GCM_TAG_BITS, nonce)
        );
        byte[] record = cipher.doFinal(ciphertext);

        int end = record.length;
        while (end > 0 && record[end - 1] == 0) {
            end--;
        }
        assertThat(record[end - 1]).isEqualTo(LAST_RECORD_DELIMITER);
        return new String(record, 0, end - 1, StandardCharsets.UTF_8);
    }

    private PublicKey toPublicKey(byte[] uncompressedPoint, ECPublicKey sameCurveKey)
            throws GeneralSecurityException {
        int coordinateLength = (uncompressedPoint.length - 1) / 2;
        ECPoint point = new ECPoint(
                new BigInteger(1, Arrays.copyOfRange(uncompressedPoint, 1, 1 + coordinateLength)),
                new BigInteger(1, Arrays.copyOfRange(uncompressedPoint, 1 + coordinateLength,
                        uncompressedPoint.length))
        );
        return KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(point, sameCurveKey.getParams()));
    }

    private byte[] info(String label) {
        return concat(label.getBytes(StandardCharsets.US_ASCII), new byte[] {0, 1});
    }

    private byte[] hmac(byte[] key, byte[] data) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    private byte[] concat(byte[]... values) {
        int length = Arrays.stream(values).mapToInt(value -> value.length).sum();
        ByteBuffer buffer = ByteBuffer.allocate(length);
        Arrays.stream(values).forEach(buffer::put);
        return buffer.array();
    }

    private record CapturedPushRequest(String method, String path, Map<String, String> headers, byte[] body) {
    }
}
