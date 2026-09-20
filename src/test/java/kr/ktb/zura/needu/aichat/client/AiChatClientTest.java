package kr.ktb.zura.needu.aichat.client;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.stream.Stream;
import kr.ktb.zura.needu.aichat.dto.response.AiServerStartSessionResponse;
import kr.ktb.zura.needu.aichat.dto.response.HealthResponse;
import kr.ktb.zura.needu.aichat.exception.AiChatErrorCode;
import kr.ktb.zura.needu.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AiChatClientTest {

    private MockRestServiceServer server;
    private AiChatClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:9000");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new AiChatClient(builder.build(), "service-token");
    }

    @Test
    void checkHealth_requestsHealthEndpoint() {
        server.expect(requestTo("http://localhost:9000/health"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer service-token"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        HealthResponse response = client.checkHealth();

        assertEquals(new HealthResponse(), response);
        server.verify();
    }

    @Test
    void startSession_requestsSessionEndpointAndParsesResponse() {
        server.expect(requestTo("http://localhost:9000/v1/chat/sessions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"userId": 1, "conversationRoomId": 101}
                        """))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body("""
                        {"sessionId": 101, "greeting": "안녕하세요", "maxTurns": 20}
                        """));

        AiServerStartSessionResponse response = client.startSession(1L, 101L);

        assertEquals(new AiServerStartSessionResponse(101L, "안녕하세요", 20), response);
        server.verify();
    }

    @ParameterizedTest
    @MethodSource("httpErrors")
    void httpError_request_throwsMappedBusinessException(HttpStatus status, AiChatErrorCode errorCode) {
        server.expect(requestTo("http://localhost:9000/health"))
                .andRespond(withStatus(status));

        BusinessException exception = assertThrows(BusinessException.class,
                client::checkHealth);

        assertEquals(errorCode, exception.getErrorCode());
    }

    @Test
    void timeout_request_throwsRequestTimeout() {
        server.expect(requestTo("http://localhost:9000/health"))
                .andRespond(request -> {
                    throw new ResourceAccessException("timeout", new SocketTimeoutException());
                });

        BusinessException exception = assertThrows(BusinessException.class,
                client::checkHealth);

        assertEquals(AiChatErrorCode.AICHAT_REQUEST_TIMEOUT, exception.getErrorCode());
    }

    @Test
    void networkError_request_throwsServerUnavailable() {
        server.expect(requestTo("http://localhost:9000/health"))
                .andRespond(request -> {
                    throw new ResourceAccessException("connection refused", new ConnectException());
                });

        BusinessException exception = assertThrows(BusinessException.class,
                client::checkHealth);

        assertEquals(AiChatErrorCode.AICHAT_SERVER_UNAVAILABLE, exception.getErrorCode());
    }

    @Test
    void invalidJson_request_throwsInvalidResponse() {
        server.expect(requestTo("http://localhost:9000/health"))
                .andRespond(withSuccess("not-json", MediaType.APPLICATION_JSON));

        BusinessException exception = assertThrows(BusinessException.class,
                client::checkHealth);

        assertEquals(AiChatErrorCode.AICHAT_INVALID_RESPONSE, exception.getErrorCode());
    }

    private static Stream<Arguments> httpErrors() {
        return Stream.of(
                Arguments.of(HttpStatus.UNAUTHORIZED, AiChatErrorCode.AICHAT_AUTHENTICATION_FAILED),
                Arguments.of(HttpStatus.BAD_REQUEST, AiChatErrorCode.AICHAT_REQUEST_REJECTED),
                Arguments.of(HttpStatus.INTERNAL_SERVER_ERROR, AiChatErrorCode.AICHAT_SERVER_UNAVAILABLE)
        );
    }
}
