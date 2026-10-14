package kr.ktb.zura.needu.friend.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import kr.ktb.zura.needu.auth.exception.AuthErrorCode;
import kr.ktb.zura.needu.auth.service.AuthService;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.friend.client.KakaoFriendClient;
import kr.ktb.zura.needu.friend.entity.FriendSyncRequest;
import kr.ktb.zura.needu.friend.repository.FriendSyncRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

@Service
public class FriendSyncService {

    private static final Logger log = LoggerFactory.getLogger(FriendSyncService.class);
    private static final String FRIENDS_SCOPE = "friends";

    private final FriendSyncRequestRepository friendSyncRequestRepository;
    private final AuthService authService;
    private final KakaoFriendClient kakaoFriendClient;
    private final FriendService friendService;
    private final int batchSize;
    private final int maxRetryCount;
    private final Duration retryDelay;

    public FriendSyncService(
            FriendSyncRequestRepository friendSyncRequestRepository,
            AuthService authService,
            KakaoFriendClient kakaoFriendClient,
            FriendService friendService,
            @Value("${needu.friend.sync.batch-size}") int batchSize,
            @Value("${needu.friend.sync.max-retry-count}") int maxRetryCount,
            @Value("${needu.friend.sync.retry-delay}") Duration retryDelay
    ) {
        this.friendSyncRequestRepository = friendSyncRequestRepository;
        this.authService = authService;
        this.kakaoFriendClient = kakaoFriendClient;
        this.friendService = friendService;
        this.batchSize = batchSize;
        this.maxRetryCount = maxRetryCount;
        this.retryDelay = retryDelay;
    }

    public void syncPendingRequests() {
        List<FriendSyncRequest> requests = friendSyncRequestRepository
                .findAllByNextAttemptAtLessThanEqualOrderByNextAttemptAtAscUserIdAsc(
                        LocalDateTime.now(ZoneOffset.UTC), Limit.of(batchSize));

        for (FriendSyncRequest request : requests) {
            try {
                syncRequest(request);
            } catch (RuntimeException exception) {
                log.error("친구 예약 동기화 요청 상태 저장 실패. userId={}", request.getUserId(), exception);
            }
        }
    }

    private void syncRequest(FriendSyncRequest request) {
        try {
            String accessToken = authService.findKakaoAccessToken(request.getUserId(), FRIENDS_SCOPE);
            List<Long> kakaoFriendIds = kakaoFriendClient.findAllFriendIds(accessToken);
            friendService.syncRequestedKakaoFriends(request.getUserId(), kakaoFriendIds);
            friendSyncRequestRepository.deleteById(request.getUserId());
        } catch (BusinessException exception) {
            if (exception.getErrorCode() == AuthErrorCode.AUTH_KAKAO_AUTHORIZATION_REQUIRED) {
                friendSyncRequestRepository.deleteById(request.getUserId());
                return;
            }
            retryOrDelete(request, exception);
        } catch (RuntimeException exception) {
            retryOrDelete(request, exception);
        }
    }

    private void retryOrDelete(FriendSyncRequest request, RuntimeException exception) {
        if (request.getRetryCount() >= maxRetryCount) {
            friendSyncRequestRepository.deleteById(request.getUserId());
            log.warn("친구 예약 동기화 최대 재시도 초과. userId={}", request.getUserId(), exception);
            return;
        }

        request.retryAt(LocalDateTime.now(ZoneOffset.UTC).plus(retryDelay));
        friendSyncRequestRepository.save(request);
        log.warn("친구 예약 동기화 재시도 예약. userId={}, retryCount={}",
                request.getUserId(), request.getRetryCount(), exception);
    }
}
