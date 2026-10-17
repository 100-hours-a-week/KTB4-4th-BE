package kr.ktb.zura.needu.friend.controller;

import jakarta.validation.Valid;
import java.net.URI;
import java.time.Duration;
import kr.ktb.zura.needu.auth.service.KakaoOAuthTransactionService;
import kr.ktb.zura.needu.auth.type.KakaoOAuthContext;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.response.ApiResponse;
import kr.ktb.zura.needu.common.security.AuthCookieNames;
import kr.ktb.zura.needu.friend.dto.request.FriendSearchCondition;
import kr.ktb.zura.needu.friend.dto.request.UpdateFavoriteRequest;
import kr.ktb.zura.needu.friend.dto.response.FriendDetailResponse;
import kr.ktb.zura.needu.friend.dto.response.FriendFavoriteResponse;
import kr.ktb.zura.needu.friend.dto.response.FriendListApiResponse;
import kr.ktb.zura.needu.friend.dto.response.PokeResponse;
import kr.ktb.zura.needu.friend.exception.FriendErrorCode;
import kr.ktb.zura.needu.friend.service.FriendService;
import kr.ktb.zura.needu.friend.service.KakaoFriendSyncService;
import kr.ktb.zura.needu.friend.service.PokeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@RestController
@RequestMapping("/api/v1/friends")
public class FriendController {

    private static final String KAKAO_CALLBACK_PATH = "/api/v1/friends/kakao/callback";

    private final FriendService friendService;
    private final KakaoFriendSyncService kakaoFriendSyncService;
    private final PokeService pokeService;
    private final KakaoOAuthTransactionService kakaoOAuthTransactionService;
    private final boolean cookieSecure;

    public FriendController(
            FriendService friendService,
            KakaoFriendSyncService kakaoFriendSyncService,
            PokeService pokeService,
            KakaoOAuthTransactionService kakaoOAuthTransactionService,
            @Value("${auth.cookie.secure}") boolean cookieSecure
    ) {
        this.friendService = friendService;
        this.kakaoFriendSyncService = kakaoFriendSyncService;
        this.pokeService = pokeService;
        this.kakaoOAuthTransactionService = kakaoOAuthTransactionService;
        this.cookieSecure = cookieSecure;
    }

    @GetMapping
    public ResponseEntity<FriendListApiResponse> findAllFriends(
            @AuthenticationPrincipal Long userId,
            @Valid FriendSearchCondition condition
    ) {
        return ResponseEntity.ok(FriendListApiResponse.of(
                FriendResponseMessages.FRIENDS_FOUND,
                friendService.findAllFriends(userId, condition)
        ));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<FriendDetailResponse>> findFriend(
            @AuthenticationPrincipal Long loginUserId,
            @PathVariable Long userId
    ) {
        return ResponseEntity.ok(ApiResponse.of(
                FriendResponseMessages.FRIEND_FOUND,
                friendService.findFriend(loginUserId, userId)
        ));
    }

    @PutMapping("/{userId}/favorite")
    public ResponseEntity<ApiResponse<FriendFavoriteResponse>> updateFavorite(
            @AuthenticationPrincipal Long loginUserId,
            @PathVariable Long userId,
            @Valid @RequestBody UpdateFavoriteRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.of(
                FriendResponseMessages.FAVORITE_UPDATED,
                friendService.updateFavorite(loginUserId, userId, request.isFavorite())
        ));
    }

    @PostMapping("/{userId}/pokes")
    public ResponseEntity<ApiResponse<PokeResponse>> createPoke(
            @AuthenticationPrincipal Long loginUserId,
            @PathVariable Long userId
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(
                FriendResponseMessages.POKE_CREATED,
                pokeService.createPoke(loginUserId, userId)
        ));
    }

    @GetMapping("/kakao/authorize")
    public ResponseEntity<Void> authorize(@AuthenticationPrincipal Long userId,
                                          @RequestParam String returnUrl) {
        URI returnUri = validateReturnUri(returnUrl);
        String state = kakaoOAuthTransactionService.createFriendState(userId, returnUri.toString());
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(kakaoFriendSyncService.createAuthorizationUri(userId, state))
                .header(HttpHeaders.SET_COOKIE, oauthCookie(state, kakaoOAuthTransactionService.getTtl()).toString())
                .build();
    }

    @GetMapping("/kakao/callback")
    public ResponseEntity<Void> callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            @CookieValue(name = AuthCookieNames.KAKAO_FRIEND_OAUTH, required = false) String cookieState) {
        KakaoOAuthContext context = kakaoOAuthTransactionService
                .consumeFriendState(state, cookieState)
                .orElseThrow(() -> new BusinessException(FriendErrorCode.FRIEND_KAKAO_INVALID_STATE));
        Long userId = context.userId();
        if (userId == null) {
            throw new BusinessException(FriendErrorCode.FRIEND_KAKAO_INVALID_STATE);
        }
        URI returnUri = URI.create(context.returnUrl());
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
                .header(HttpHeaders.SET_COOKIE, oauthCookie("", Duration.ZERO).toString())
                .cacheControl(CacheControl.noStore())
                .build();
    }

    private ResponseCookie oauthCookie(String value, Duration maxAge) {
        return ResponseCookie.from(AuthCookieNames.KAKAO_FRIEND_OAUTH, value)
                .httpOnly(true).secure(cookieSecure).sameSite("Lax")
                .path(KAKAO_CALLBACK_PATH).maxAge(maxAge).build();
    }
}
