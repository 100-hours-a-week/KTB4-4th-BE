package kr.ktb.zura.needu.auth.client;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import kr.ktb.zura.needu.auth.exception.AuthErrorCode;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.CommonErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KakaoOAuthClientTest {

    private MockRestServiceServer server;
    private KakaoOAuthClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new KakaoOAuthClient(builder, "rest-key", "secret",
                "http://localhost:8080/api/v1/auth/kakao/callback",
                "http://localhost:8080/api/v1/friends/kakao/callback",
                "admin-key");
    }

    @Test
    void authorizationUri_includesRegisteredRedirectUriAndState() {
        String query = URLDecoder.decode(client.createAuthorizationUri("state-123").getRawQuery(),
                StandardCharsets.UTF_8);

        assertTrue(query.contains("client_id=rest-key"));
        assertTrue(query.contains("redirect_uri=http://localhost:8080/api/v1/auth/kakao/callback"));
        assertTrue(query.contains("state=state-123"));
        assertTrue(!query.contains("scope=friends"));
    }

    @Test
    void friendAuthorizationUri_includesFriendsScopeAndFriendRedirectUri() {
        String query = URLDecoder.decode(client.createFriendAuthorizationUri("state-123").getRawQuery(),
                StandardCharsets.UTF_8);

        assertTrue(query.contains("redirect_uri=http://localhost:8080/api/v1/friends/kakao/callback"));
        assertTrue(query.contains("scope=friends"));
        assertTrue(query.contains("state=state-123"));
    }

    @Test
    void validCode_returnsKakaoUserInfo() {
        server.expect(once(), requestTo("https://kauth.kakao.com/oauth/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("client_secret=secret")))
                .andRespond(withSuccess(tokenResponse(), MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://kapi.kakao.com/v2/user/me"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer kakao-access"))
                .andRespond(withSuccess("{\"id\":42,\"kakao_account\":{\"profile\":{\"nickname\":\"테스터\",\"profile_image_url\":\"https://example.com/image.png\"}}}",
                        MediaType.APPLICATION_JSON));

        KakaoOAuthClient.KakaoUserInfo result = client.findUserInfo("code");

        assertEquals(42L, result.id());
        assertEquals("테스터", result.nickname());
        assertEquals("https://example.com/image.png", result.profileImageUrl());
        server.verify();
    }

    @Test
    void validFriendCode_returnsUserInfoAndTokens() {
        server.expect(once(), requestTo("https://kauth.kakao.com/oauth/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString(
                        "redirect_uri=http%3A%2F%2Flocalhost%3A8080%2Fapi%2Fv1%2Ffriends%2Fkakao%2Fcallback")))
                .andRespond(withSuccess(tokenResponse(), MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://kapi.kakao.com/v2/user/me"))
                .andExpect(header("Authorization", "Bearer kakao-access"))
                .andRespond(withSuccess("{\"id\":42}", MediaType.APPLICATION_JSON));

        KakaoOAuthClient.KakaoAuthorization result = client.authorizeFriend("code");

        assertEquals(42L, result.userInfo().id());
        assertEquals("kakao-access", result.tokens().accessToken());
        assertEquals("kakao-refresh", result.tokens().refreshToken());
        assertEquals(Duration.ofSeconds(21600), result.tokens().accessTokenExpiresIn());
        assertEquals(Duration.ofSeconds(5184000), result.tokens().refreshTokenExpiresIn());
        assertEquals(Set.of("friends", "profile_nickname"), result.tokens().scopes());
        server.verify();
    }

    @Test
    void invalidCode_returnsBadRequest() {
        server.expect(once(), requestTo("https://kauth.kakao.com/oauth/token"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> client.findUserInfo("invalid"));

        assertEquals(AuthErrorCode.AUTH_KAKAO_INVALID_CODE, exception.getErrorCode());
        server.verify();
    }

    @Test
    void linkedUser_unlink_sendsAdminRequest() {
        server.expect(once(), requestTo("https://kapi.kakao.com/v1/user/unlink"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "KakaoAK admin-key"))
                .andExpect(content().string(containsString("target_id_type=user_id")))
                .andExpect(content().string(containsString("target_id=42")))
                .andRespond(withSuccess("{\"id\":42}", MediaType.APPLICATION_JSON));

        client.unlink(42L);

        server.verify();
    }

    @Test
    void responseForDifferentUser_unlink_returnsServiceUnavailable() {
        server.expect(once(), requestTo("https://kapi.kakao.com/v1/user/unlink"))
                .andRespond(withSuccess("{\"id\":43}", MediaType.APPLICATION_JSON));

        BusinessException exception = assertThrows(BusinessException.class, () -> client.unlink(42L));

        assertEquals(CommonErrorCode.COMMON_SERVICE_UNAVAILABLE, exception.getErrorCode());
        server.verify();
    }

    @Test
    void alreadyUnlinkedUser_unlink_returnsNormally() {
        server.expect(once(), requestTo("https://kapi.kakao.com/v1/user/unlink"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":-101,\"msg\":\"NotRegisteredUserException\"}"));

        client.unlink(42L);

        server.verify();
    }

    @Test
    void kakaoUnavailable_unlink_returnsServiceUnavailable() {
        server.expect(once(), requestTo("https://kapi.kakao.com/v1/user/unlink"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        BusinessException exception = assertThrows(BusinessException.class, () -> client.unlink(42L));

        assertEquals(CommonErrorCode.COMMON_SERVICE_UNAVAILABLE, exception.getErrorCode());
        server.verify();
    }

    @Test
    void malformedKakaoError_unlink_returnsServiceUnavailable() {
        server.expect(once(), requestTo("https://kapi.kakao.com/v1/user/unlink"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("not-json"));

        BusinessException exception = assertThrows(BusinessException.class, () -> client.unlink(42L));

        assertEquals(CommonErrorCode.COMMON_SERVICE_UNAVAILABLE, exception.getErrorCode());
        server.verify();
    }

    private String tokenResponse() {
        return """
                {
                  "access_token": "kakao-access",
                  "refresh_token": "kakao-refresh",
                  "expires_in": 21600,
                  "refresh_token_expires_in": 5184000,
                  "scope": "friends profile_nickname"
                }
                """;
    }
}
