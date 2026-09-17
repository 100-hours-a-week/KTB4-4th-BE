package kr.ktb.zura.needu.auth.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.URI;
import kr.ktb.zura.needu.auth.type.AuthErrorCode;
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

    public KakaoOAuthClient(RestClient.Builder restClientBuilder,
                            @Value("${kakao.oauth.client-id}") String clientId,
                            @Value("${kakao.oauth.client-secret}") String clientSecret,
                            @Value("${kakao.oauth.redirect-uri}") String redirectUri) {
        this.restClient = restClientBuilder.build();
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
    }

    public URI createAuthorizationUri(String state) {
        return UriComponentsBuilder.fromUriString(AUTHORIZE_URI)
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("state", state)
                .build().encode().toUri();
    }

    public KakaoUserInfo findUserInfo(String code) {
        String accessToken = requestAccessToken(code);
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

    private String requestAccessToken(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("redirect_uri", redirectUri);
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

    private record KakaoTokenResponse(@JsonProperty("access_token") String accessToken) {
    }

    private record KakaoUserResponse(Long id, @JsonProperty("kakao_account") KakaoAccount kakaoAccount) {
    }

    private record KakaoAccount(KakaoProfile profile) {
    }

    private record KakaoProfile(String nickname, @JsonProperty("profile_image_url") String profileImageUrl) {
    }
}
