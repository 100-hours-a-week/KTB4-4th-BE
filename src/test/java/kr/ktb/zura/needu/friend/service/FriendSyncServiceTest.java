package kr.ktb.zura.needu.friend.service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import kr.ktb.zura.needu.auth.exception.AuthErrorCode;
import kr.ktb.zura.needu.auth.service.AuthService;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.friend.client.KakaoFriendClient;
import kr.ktb.zura.needu.friend.entity.FriendSyncRequest;
import kr.ktb.zura.needu.friend.repository.FriendSyncRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Limit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FriendSyncServiceTest {

    private FriendSyncRequestRepository friendSyncRequestRepository;
    private AuthService authService;
    private KakaoFriendClient kakaoFriendClient;
    private FriendService friendService;
    private FriendSyncService friendSyncService;

    @BeforeEach
    void setUp() {
        friendSyncRequestRepository = mock(FriendSyncRequestRepository.class);
        authService = mock(AuthService.class);
        kakaoFriendClient = mock(KakaoFriendClient.class);
        friendService = mock(FriendService.class);
        friendSyncService = new FriendSyncService(
                friendSyncRequestRepository,
                authService,
                kakaoFriendClient,
                friendService,
                20,
                3,
                java.time.Duration.ofMinutes(5));
    }

    @Test
    void successfulRequest_syncPendingRequests_syncsFriendsAndDeletesRequest() {
        FriendSyncRequest request = request(1L);
        whenDueRequests(request);
        when(authService.findKakaoAccessToken(1L, "friends")).thenReturn("access-token");
        when(kakaoFriendClient.findAllFriendIds("access-token")).thenReturn(List.of(20L, 30L));

        friendSyncService.syncPendingRequests();

        verify(friendService).syncRequestedKakaoFriends(1L, List.of(20L, 30L));
        verify(friendSyncRequestRepository).deleteById(1L);
        verify(friendSyncRequestRepository, never()).save(request);
    }

    @Test
    void authorizationRequired_syncPendingRequests_deletesRequestWithoutSyncingFriends() {
        FriendSyncRequest request = request(1L);
        whenDueRequests(request);
        when(authService.findKakaoAccessToken(1L, "friends"))
                .thenThrow(new BusinessException(AuthErrorCode.AUTH_KAKAO_AUTHORIZATION_REQUIRED));

        friendSyncService.syncPendingRequests();

        verify(friendSyncRequestRepository).deleteById(1L);
        verify(kakaoFriendClient, never()).findAllFriendIds(any());
        verify(friendService, never()).syncRequestedKakaoFriends(any(), any());
    }

    @Test
    void temporaryFailure_syncPendingRequests_schedulesRetryAndContinuesNextRequest() {
        FriendSyncRequest failedRequest = request(1L);
        FriendSyncRequest successfulRequest = request(2L);
        whenDueRequests(failedRequest, successfulRequest);
        when(authService.findKakaoAccessToken(1L, "friends")).thenReturn("failed-token");
        when(authService.findKakaoAccessToken(2L, "friends")).thenReturn("valid-token");
        when(kakaoFriendClient.findAllFriendIds("failed-token"))
                .thenThrow(new IllegalStateException());
        when(kakaoFriendClient.findAllFriendIds("valid-token")).thenReturn(List.of(30L));
        LocalDateTime beforeSync = LocalDateTime.now(ZoneOffset.UTC);

        friendSyncService.syncPendingRequests();

        assertThat(failedRequest.getRetryCount()).isEqualTo(1);
        assertThat(failedRequest.getNextAttemptAt()).isBetween(
                beforeSync.plusMinutes(5), LocalDateTime.now(ZoneOffset.UTC).plusMinutes(5));
        verify(friendSyncRequestRepository).save(failedRequest);
        verify(friendService).syncRequestedKakaoFriends(2L, List.of(30L));
        verify(friendSyncRequestRepository).deleteById(2L);
    }

    @Test
    void fourthFailure_syncPendingRequests_deletesRequest() {
        FriendSyncRequest request = request(1L);
        request.retryAt(LocalDateTime.now(ZoneOffset.UTC));
        request.retryAt(LocalDateTime.now(ZoneOffset.UTC));
        request.retryAt(LocalDateTime.now(ZoneOffset.UTC));
        whenDueRequests(request);
        when(authService.findKakaoAccessToken(1L, "friends"))
                .thenThrow(new IllegalStateException());

        friendSyncService.syncPendingRequests();

        verify(friendSyncRequestRepository).deleteById(1L);
        verify(friendSyncRequestRepository, never()).save(request);
    }

    private FriendSyncRequest request(Long userId) {
        return new FriendSyncRequest(userId, LocalDateTime.now(ZoneOffset.UTC));
    }

    private void whenDueRequests(FriendSyncRequest... requests) {
        when(friendSyncRequestRepository
                .findAllByNextAttemptAtLessThanEqualOrderByNextAttemptAtAscUserIdAsc(
                        any(LocalDateTime.class), eq(Limit.of(20))))
                .thenReturn(List.of(requests));
    }
}
