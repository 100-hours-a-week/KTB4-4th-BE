package kr.ktb.zura.needu.auth.service;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import kr.ktb.zura.needu.auth.client.KakaoOAuthClient;
import kr.ktb.zura.needu.auth.dto.response.AuthSessionResponse;
import kr.ktb.zura.needu.auth.exception.AuthErrorCode;
import kr.ktb.zura.needu.auth.config.KakaoOAuth2ClientConfig;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.user.dto.response.UserResponse;
import kr.ktb.zura.needu.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final KakaoOAuthClient kakaoOAuthClient;
    private final UserService userService;
    private final AuthSessionService authSessionService;
    private final AccessTokenService accessTokenService;
    private final KakaoTokenService kakaoTokenService;
    private final OAuth2AuthorizedClientManager kakaoAuthorizedClientManager;

    public URI createKakaoAuthorizationUri(String state) {
        return kakaoOAuthClient.createAuthorizationUri(state);
    }

    public URI createKakaoFriendAuthorizationUri(String state) {
        return kakaoOAuthClient.createFriendAuthorizationUri(state);
    }

    public void authorizeKakaoFriend(Long userId, String code) {
        KakaoOAuthClient.KakaoAuthorization authorization = kakaoOAuthClient.authorizeFriend(code);
        userService.validateKakaoIdentity(userId, authorization.userInfo().id());
        kakaoTokenService.saveTokens(userId, authorization.tokens());
    }

    public String findKakaoAccessToken(Long userId, String requiredScope) {
        UsernamePasswordAuthenticationToken principal = new UsernamePasswordAuthenticationToken(
                userId.toString(), null, List.of());
        OAuth2AuthorizeRequest request = OAuth2AuthorizeRequest
                .withClientRegistrationId(KakaoOAuth2ClientConfig.REGISTRATION_ID)
                .principal(principal)
                .build();
        OAuth2AuthorizedClient authorizedClient;
        try {
            authorizedClient = kakaoAuthorizedClientManager.authorize(request);
        } catch (OAuth2AuthorizationException exception) {
            if ("invalid_grant".equals(exception.getError().getErrorCode())) {
                throw new BusinessException(AuthErrorCode.AUTH_KAKAO_AUTHORIZATION_REQUIRED);
            }
            throw new BusinessException(AuthErrorCode.AUTH_KAKAO_UNAVAILABLE, null, exception);
        }
        if (authorizedClient == null
                || !authorizedClient.getAccessToken().getScopes().contains(requiredScope)) {
            throw new BusinessException(AuthErrorCode.AUTH_KAKAO_AUTHORIZATION_REQUIRED);
        }
        return authorizedClient.getAccessToken().getTokenValue();
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

    public void unlinkKakaoAccount(Long externalId) {
        kakaoOAuthClient.unlink(externalId);
    }

    @Transactional
    public void deleteUserAuthentication(Long userId) {
        authSessionService.deleteAllByUserId(userId);
        kakaoTokenService.deleteByUserId(userId);
    }

    public AuthSessionResponse findSession(Long userId) {
        return AuthSessionResponse.from(userService.findAuthenticatedUser(userId));
    }

    private Tokens toTokens(AuthSessionService.RefreshToken refresh) {
        return new Tokens(accessTokenService.issueAccessToken(refresh.userId()),
                refresh.value(), refresh.remaining());
    }

    public record Tokens(String accessToken, String refreshToken, Duration refreshMaxAge) {
    }
}
