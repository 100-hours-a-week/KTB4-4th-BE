package kr.ktb.zura.needu.auth.service;

import java.net.URI;
import java.time.Duration;
import kr.ktb.zura.needu.auth.client.KakaoOAuthClient;
import kr.ktb.zura.needu.user.dto.response.UserResponse;
import kr.ktb.zura.needu.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final KakaoOAuthClient kakaoOAuthClient;
    private final UserService userService;
    private final AuthSessionService authSessionService;
    private final AccessTokenService accessTokenService;

    public URI createKakaoAuthorizationUri(String state) {
        return kakaoOAuthClient.createAuthorizationUri(state);
    }

    public Tokens loginWithKakao(String code) {
        KakaoOAuthClient.KakaoUserInfo kakaoUser = kakaoOAuthClient.findUserInfo(code);
        UserResponse user = userService.findOrCreateKakaoUser(
                kakaoUser.id(), kakaoUser.nickname(), kakaoUser.profileImageUrl());
        AuthSessionService.RefreshToken refresh = authSessionService.create(user.id());
        return toTokens(refresh);
    }

    @Transactional
    public Tokens refresh(String refreshToken) {
        return toTokens(authSessionService.rotate(refreshToken));
    }

    public void logout(String refreshToken) {
        authSessionService.revoke(refreshToken);
    }

    private Tokens toTokens(AuthSessionService.RefreshToken refresh) {
        return new Tokens(accessTokenService.issueAccessToken(refresh.userId()),
                refresh.value(), refresh.remaining());
    }

    public record Tokens(String accessToken, String refreshToken, Duration refreshMaxAge) {
    }
}
