package kr.ktb.zura.needu.friend.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import java.util.UUID;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.response.ApiResponse;
import kr.ktb.zura.needu.common.response.CursorApiResponse;
import kr.ktb.zura.needu.friend.dto.response.FriendDetailResponse;
import kr.ktb.zura.needu.friend.dto.response.FriendSummaryResponse;
import kr.ktb.zura.needu.friend.exception.FriendErrorCode;
import kr.ktb.zura.needu.friend.service.FriendService;
import kr.ktb.zura.needu.friend.service.KakaoFriendSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/friends")
public class FriendController {

    private static final String FRIEND_FOUND_MESSAGE = "친구 정보를 조회했습니다.";
    private static final String FRIENDS_FOUND_MESSAGE = "친구 목록 조회에 성공했습니다.";
    private static final int MIN_PAGE_SIZE = 1;
    private static final int MAX_PAGE_SIZE = 50;
    private static final String KAKAO_FRIEND_STATE = "kakaoFriendOAuthState";
    private static final String KAKAO_FRIEND_USER_ID = "kakaoFriendOAuthUserId";
    private static final String KAKAO_FRIEND_RETURN_URL = "kakaoFriendOAuthReturnUrl";

    private final FriendService friendService;
    private final KakaoFriendSyncService kakaoFriendSyncService;

    @GetMapping
    public ResponseEntity<CursorApiResponse<FriendSummaryResponse>> findAllFriends(
            @AuthenticationPrincipal Long userId,
            @RequestParam @Pattern(regexp = "birthday") String sort,
            @RequestParam(required = false) String cursor,
            @RequestParam @Min(MIN_PAGE_SIZE) @Max(MAX_PAGE_SIZE) int size
    ) {
        return ResponseEntity.ok(CursorApiResponse.of(
                FRIENDS_FOUND_MESSAGE,
                friendService.findAllFriends(userId, cursor, size)
        ));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<FriendDetailResponse>> findFriend(
            @AuthenticationPrincipal Long loginUserId,
            @PathVariable Long userId
    ) {
        return ResponseEntity.ok(ApiResponse.of(FRIEND_FOUND_MESSAGE, friendService.findFriend(loginUserId, userId)));
    }

    @GetMapping("/kakao/authorize")
    public ResponseEntity<Void> authorize(@AuthenticationPrincipal Long userId,
                                          @RequestParam String returnUrl,
                                          HttpServletRequest request) {
        URI returnUri = validateReturnUri(returnUrl);
        String state = UUID.randomUUID().toString();
        HttpSession session = request.getSession(true);
        session.setAttribute(KAKAO_FRIEND_STATE, state);
        session.setAttribute(KAKAO_FRIEND_USER_ID, userId);
        session.setAttribute(KAKAO_FRIEND_RETURN_URL, returnUri);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(kakaoFriendSyncService.createAuthorizationUri(state))
                .build();
    }

    @GetMapping("/kakao/callback")
    public ResponseEntity<Void> callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        String expectedState = session == null ? null : (String) session.getAttribute(KAKAO_FRIEND_STATE);
        Long userId = session == null ? null : (Long) session.getAttribute(KAKAO_FRIEND_USER_ID);
        URI returnUri = session == null ? null : (URI) session.getAttribute(KAKAO_FRIEND_RETURN_URL);
        if (session != null) {
            session.invalidate();
        }
        if (userId == null || expectedState == null || !expectedState.equals(state) || returnUri == null) {
            throw new BusinessException(FriendErrorCode.FRIEND_KAKAO_INVALID_STATE);
        }
        if (error != null) {
            return redirect(returnUri, "cancelled");
        }
        if (code == null || code.isBlank()) {
            return redirect(returnUri, "failed");
        }
        try {
            kakaoFriendSyncService.sync(userId, code);
            return redirect(returnUri, "success");
        } catch (Exception exception) {
            log.warn("카카오 친구 동기화 실패 userId={}", userId, exception);
            return redirect(returnUri, "failed");
        }
    }

    private URI validateReturnUri(String returnUrl) {
        URI returnUri;
        try {
            returnUri = URI.create(returnUrl);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(FriendErrorCode.FRIEND_KAKAO_INVALID_RETURN_URL);
        }
        if (!("http".equalsIgnoreCase(returnUri.getScheme()) || "https".equalsIgnoreCase(returnUri.getScheme()))
                || returnUri.getHost() == null || returnUri.getUserInfo() != null) {
            throw new BusinessException(FriendErrorCode.FRIEND_KAKAO_INVALID_RETURN_URL);
        }
        return returnUri;
    }

    private ResponseEntity<Void> redirect(URI returnUri, String result) {
        URI location = UriComponentsBuilder.fromUri(returnUri)
                .queryParam("kakaoFriendSync", result)
                .build().encode().toUri();
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(location)
                .cacheControl(CacheControl.noStore())
                .build();
    }
}
