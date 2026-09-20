package kr.ktb.zura.needu.friend.client;

import java.util.List;
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
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KakaoFriendClientTest {

    private MockRestServiceServer server;
    private KakaoFriendClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new KakaoFriendClient(builder);
    }

    @Test
    void multiplePages_returnsAllFriendIds() {
        server.expect(once(), requestTo(
                        "https://kapi.kakao.com/v1/api/talk/friends?offset=0&limit=100"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer kakao-access"))
                .andRespond(withSuccess("{\"elements\":[{\"id\":10}],\"total_count\":2}",
                        MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(
                        "https://kapi.kakao.com/v1/api/talk/friends?offset=1&limit=100"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"elements\":[{\"id\":20}],\"total_count\":2}",
                        MediaType.APPLICATION_JSON));

        assertEquals(List.of(10L, 20L), client.findAllFriendIds("kakao-access"));
        server.verify();
    }

    @Test
    void laterPageFailure_throwsWithoutReturningPartialResult() {
        server.expect(once(), requestTo(
                        "https://kapi.kakao.com/v1/api/talk/friends?offset=0&limit=100"))
                .andRespond(withSuccess("{\"elements\":[{\"id\":10}],\"total_count\":2}",
                        MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(
                        "https://kapi.kakao.com/v1/api/talk/friends?offset=1&limit=100"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThrows(BusinessException.class, () -> client.findAllFriendIds("kakao-access"));
        server.verify();
    }
}
