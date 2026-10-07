package kr.ktb.zura.needu.user.controller;

import java.time.Duration;
import kr.ktb.zura.needu.common.response.ApiResponse;
import kr.ktb.zura.needu.common.security.AuthCookieNames;
import kr.ktb.zura.needu.user.dto.response.MyPageResponse;
import kr.ktb.zura.needu.user.service.UserService;
import kr.ktb.zura.needu.user.service.UserWithdrawalService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me")
public class UserController {

    private final UserService userService;
    private final UserWithdrawalService userWithdrawalService;
    private final boolean cookieSecure;

    public UserController(UserService userService, UserWithdrawalService userWithdrawalService,
                          @Value("${auth.cookie.secure}") boolean cookieSecure) {
        this.userService = userService;
        this.userWithdrawalService = userWithdrawalService;
        this.cookieSecure = cookieSecure;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<MyPageResponse>> findMyPage(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.of(UserResponseMessages.MY_PAGE_FOUND, userService.findMyPage(userId)));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> withdraw(
            @AuthenticationPrincipal Long userId,
            @CookieValue(name = AuthCookieNames.REFRESH_TOKEN, required = false) String refreshToken
    ) {
        userWithdrawalService.withdraw(userId, refreshToken);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, expiredCookie(AuthCookieNames.ACCESS_TOKEN).toString())
                .header(HttpHeaders.SET_COOKIE, expiredCookie(AuthCookieNames.REFRESH_TOKEN).toString())
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.of(UserResponseMessages.WITHDRAWN, null));
    }

    private ResponseCookie expiredCookie(String name) {
        return ResponseCookie.from(name, "")
                .httpOnly(true).secure(cookieSecure).sameSite("Lax").path("/").maxAge(Duration.ZERO).build();
    }
}
