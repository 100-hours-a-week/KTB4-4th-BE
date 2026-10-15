package kr.ktb.zura.needu.notification.service;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.CommonErrorCode;
import kr.ktb.zura.needu.notification.dto.request.CreatePushSubscriptionRequest;
import kr.ktb.zura.needu.notification.dto.request.DeletePushSubscriptionRequest;
import kr.ktb.zura.needu.notification.dto.response.PushSubscriptionResponse;
import kr.ktb.zura.needu.notification.entity.PushSubscription;
import kr.ktb.zura.needu.notification.repository.PushSubscriptionRepository;
import kr.ktb.zura.needu.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PushSubscriptionService {

    private static final Set<String> ALLOWED_PUSH_SERVICE_HOSTS = Set.of(
            "fcm.googleapis.com",
            "updates.push.services.mozilla.com"
    );
    private static final Set<String> ALLOWED_PUSH_SERVICE_HOST_SUFFIXES = Set.of(
            ".push.apple.com",
            ".notify.windows.com"
    );

    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final UserService userService;

    @Transactional
    public PushSubscriptionResponse subscribe(Long userId, CreatePushSubscriptionRequest request) {
        userService.validateActiveUser(userId);
        validateAllowedPushService(request.endpoint());
        String endpointHash = hash(request.endpoint());
        pushSubscriptionRepository.upsert(
                userId,
                request.endpoint(),
                endpointHash,
                request.keys().p256dh(),
                request.keys().auth()
        );
        PushSubscription subscription = pushSubscriptionRepository.findByEndpointHash(endpointHash)
                .orElseThrow(() -> new IllegalStateException("Saved push subscription not found"));
        return new PushSubscriptionResponse(true, subscription.getUpdatedAt());
    }

    @Transactional
    public void unsubscribe(Long userId, DeletePushSubscriptionRequest request) {
        userService.validateActiveUser(userId);
        validateAllowedPushService(request.endpoint());
        pushSubscriptionRepository.deleteByEndpointHashAndUserId(hash(request.endpoint()), userId);
    }

    private void validateAllowedPushService(String endpoint) {
        try {
            URI uri = URI.create(endpoint);
            String host = uri.getHost();
            boolean isAllowedHost = host != null && isAllowedHost(host.toLowerCase(Locale.ROOT));
            boolean isAllowedPort = uri.getPort() == -1 || uri.getPort() == 443;
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || uri.getUserInfo() != null
                    || !isAllowedPort
                    || !isAllowedHost) {
                throw new BusinessException(CommonErrorCode.COMMON_INVALID_INPUT);
            }
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(CommonErrorCode.COMMON_INVALID_INPUT);
        }
    }

    private boolean isAllowedHost(String host) {
        return ALLOWED_PUSH_SERVICE_HOSTS.contains(host)
                || ALLOWED_PUSH_SERVICE_HOST_SUFFIXES.stream().anyMatch(host::endsWith);
    }

    private String hash(String endpoint) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(endpoint.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
