package kr.ktb.zura.needu.auth.client;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import kr.ktb.zura.needu.auth.type.AuthErrorCode;
import kr.ktb.zura.needu.common.exception.BusinessException;
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
                "http://localhost:8080/api/v1/auth/kakao/callback");
    }

    @Test
    void authorizationUri_includesRegisteredRedirectUriAndState() {
        String query = URLDecoder.decode(client.createAuthorizationUri("state-123").getRawQuery(),
                StandardCharsets.UTF_8);

        assertTrue(query.contains("client_id=rest-key"));
        assertTrue(query.contains("redirect_uri=http://localhost:8080/api/v1/auth/kakao/callback"));
        assertTrue(query.contains("state=state-123"));
    }

    @Test
    void validCode_returnsKakaoUserInfo() {
        server.expect(once(), requestTo("https://kauth.kakao.com/oauth/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("client_secret=secret")))
                .andRespond(withSuccess("{\"access_token\":\"kakao-access\"}", MediaType.APPLICATION_JSON));
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
    void invalidCode_returnsBadRequest() {
        server.expect(once(), requestTo("https://kauth.kakao.com/oauth/token"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> client.findUserInfo("invalid"));

        assertEquals(AuthErrorCode.AUTH_KAKAO_INVALID_CODE, exception.getErrorCode());
        server.verify();
    }
}
