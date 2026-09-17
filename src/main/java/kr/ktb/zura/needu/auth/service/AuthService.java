package kr.ktb.zura.needu.auth.service;

import java.net.URI;
import kr.ktb.zura.needu.auth.client.KakaoOAuthClient;
import kr.ktb.zura.needu.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final KakaoOAuthClient kakaoOAuthClient;
    private final UserService userService;

    public URI createKakaoAuthorizationUri(String state) {
        return kakaoOAuthClient.createAuthorizationUri(state);
    }

    public void loginWithKakao(String code) {
        KakaoOAuthClient.KakaoUserInfo kakaoUser = kakaoOAuthClient.findUserInfo(code);
        userService.findOrCreateKakaoUser(
                kakaoUser.id(), kakaoUser.nickname(), kakaoUser.profileImageUrl());
    }
}
