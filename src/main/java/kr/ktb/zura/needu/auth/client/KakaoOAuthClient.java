package kr.ktb.zura.needu.auth.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.URI;
import kr.ktb.zura.needu.auth.exception.AuthErrorCode;
import kr.ktb.zura.needu.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class KakaoOAuthClient {

    private static final String AUTHORIZE_URI = "https://kauth.kakao.com/oauth/authorize";
    private static final String TOKEN_URI = "https://kauth.kakao.com/oauth/token";
    private static final String USER_INFO_URI = "https://kapi.kakao.com/v2/user/me";

    private final RestClient restClient;
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final String friendRedirectUri;

    public KakaoOAuthClient(RestClient.Builder restClientBuilder,
                            @Value("${kakao.oauth.client-id}") String clientId,
                            @Value("${kakao.oauth.client-secret}") String clientSecret,
                            @Value("${kakao.oauth.redirect-uri}") String redirectUri,
                            @Value("${kakao.oauth.friend-redirect-uri}") String friendRedirectUri) {
        this.restClient = restClientBuilder.build();
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
        this.friendRedirectUri = friendRedirectUri;
    }

    public URI createAuthorizationUri(String state) {
        return UriComponentsBuilder.fromUriString(AUTHORIZE_URI)
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("state", state)
                .build().encode().toUri();
    }

    public URI createFriendAuthorizationUri(String state) {
        return UriComponentsBuilder.fromUriString(AUTHORIZE_URI)
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", friendRedirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", "friends")
                .queryParam("state", state)
                .build().encode().toUri();
    }

    public KakaoUserInfo findUserInfo(String code) {
        return requestUserInfo(requestAccessToken(code, redirectUri));
    }

    public KakaoAuthorization authorizeFriend(String code) {
        String accessToken = requestAccessToken(code, friendRedirectUri);
        return new KakaoAuthorization(requestUserInfo(accessToken), accessToken);
    }

    private KakaoUserInfo requestUserInfo(String accessToken) {
        try {
            KakaoUserResponse response = restClient.get()
                    .uri(USER_INFO_URI)
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .body(KakaoUserResponse.class);
            if (response == null || response.id() == null) {
                throw new BusinessException(AuthErrorCode.AUTH_KAKAO_UNAVAILABLE);
            }
            KakaoProfile profile = response.kakaoAccount() == null
                    ? null : response.kakaoAccount().profile();
            return new KakaoUserInfo(response.id(),
                    profile == null ? null : profile.nickname(),
                    profile == null ? null : profile.profileImageUrl());
        } catch (RestClientException exception) {
            throw new BusinessException(AuthErrorCode.AUTH_KAKAO_UNAVAILABLE);
        }
    }

    private String requestAccessToken(String code, String requestRedirectUri) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("redirect_uri", requestRedirectUri);
        form.add("code", code);
        try {
            KakaoTokenResponse response = restClient.post()
                    .uri(TOKEN_URI)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(KakaoTokenResponse.class);
            if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
                throw new BusinessException(AuthErrorCode.AUTH_KAKAO_UNAVAILABLE);
            }
            return response.accessToken();
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().is4xxClientError()) {
                throw new BusinessException(AuthErrorCode.AUTH_KAKAO_INVALID_CODE);
            }
            throw new BusinessException(AuthErrorCode.AUTH_KAKAO_UNAVAILABLE);
        } catch (RestClientException exception) {
            throw new BusinessException(AuthErrorCode.AUTH_KAKAO_UNAVAILABLE);
        }
    }

    public record KakaoUserInfo(Long id, String nickname, String profileImageUrl) {
    }

    public record KakaoAuthorization(KakaoUserInfo userInfo, String accessToken) {
    }

    private record KakaoTokenResponse(@JsonProperty("access_token") String accessToken) {
    }

    private record KakaoUserResponse(Long id, @JsonProperty("kakao_account") KakaoAccount kakaoAccount) {
    }

    private record KakaoAccount(KakaoProfile profile) {
    }

    private record KakaoProfile(String nickname, @JsonProperty("profile_image_url") String profileImageUrl) {
    }
}
