package kr.ktb.zura.needu.aichat.client;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.stream.Stream;
import kr.ktb.zura.needu.aichat.dto.response.AiServerAnalysisKeywordsResponse;
import kr.ktb.zura.needu.aichat.dto.response.AiServerAnalysisResponse;
import kr.ktb.zura.needu.aichat.dto.response.AiServerHealthResponse;
import kr.ktb.zura.needu.aichat.dto.response.AiServerSendMessageResponse;
import kr.ktb.zura.needu.aichat.dto.response.AiServerStartSessionResponse;
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
    private MockRestServiceServer messageServer;
    private AiChatClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:9000");
        server = MockRestServiceServer.bindTo(builder).build();
        // 메시지 전송은 read timeout이 다른 전용 RestClient를 쓰므로 대역도 따로 둔다
        RestClient.Builder messageBuilder = RestClient.builder().baseUrl("http://localhost:9000");
        messageServer = MockRestServiceServer.bindTo(messageBuilder).build();
        client = new AiChatClient(builder.build(), messageBuilder.build(), "service-token");
    }

    @Test
    void checkHealth_requestsHealthEndpoint() {
        server.expect(requestTo("http://localhost:9000/health"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer service-token"))
                .andRespond(withSuccess("""
                        {"status": "ok", "version": "0.1.0"}
                        """, MediaType.APPLICATION_JSON));

        AiServerHealthResponse response = client.checkHealth();

        assertEquals(new AiServerHealthResponse("ok", "0.1.0"), response);
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

    @Test
    void sendMessage_requestsMessageEndpointAndParsesReply() {
        messageServer.expect(requestTo("http://localhost:9000/v1/chat/sessions/101/messages"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer service-token"))
                .andExpect(content().json("""
                        {"message": "주말마다 캠핑 가요"}
                        """))
                .andRespond(withSuccess("""
                        {
                          "reply": "캠핑 좋죠.",
                          "turn": 3,
                          "maxTurns": 20,
                          "canClose": false,
                          "itemCount": 4,
                          "inputLocked": false,
                          "completionReason": null,
                          "profileCompleteness": null,
                          "lastTurnExtractionFailed": false
                        }
                        """, MediaType.APPLICATION_JSON));

        AiServerSendMessageResponse response = client.sendMessage(101L, "주말마다 캠핑 가요");

        assertEquals(new AiServerSendMessageResponse(
                "캠핑 좋죠.", 3, 20, false, 4, false, null, null, false), response);
        messageServer.verify();
    }

    @Test
    void createAnalysis_requestsAnalysisEndpointAndParsesResponse() {
        server.expect(requestTo("http://localhost:9000/v1/chat/sessions/101/analysis"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer service-token"))
                .andRespond(withSuccess("""
                        {
                          "profile": {"schemaVersion": "3.0", "userId": 10293},
                          "summary": "캠핑과 핸드드립을 즐깁니다.",
                          "keywords": {
                            "taste": ["핸드드립", "가벼운 장비"],
                            "interest": ["캠핑", "티타늄 머그컵"]
                          },
                          "correctionAvailable": true,
                          "profileCompleteness": "sufficient",
                          "missingSignals": []
                        }
                        """, MediaType.APPLICATION_JSON));

        AiServerAnalysisResponse response = client.createAnalysis(101L);

        assertEquals("3.0", response.profile().schemaVersion());
        assertEquals(10293L, response.profile().userId());
        assertEquals("캠핑과 핸드드립을 즐깁니다.", response.summary());
        assertEquals(new AiServerAnalysisKeywordsResponse(
                List.of("핸드드립", "가벼운 장비"),
                List.of("캠핑", "티타늄 머그컵")
        ), response.keywords());
        assertEquals(true, response.correctionAvailable());
        server.verify();
    }

    @ParameterizedTest
    @MethodSource("sendMessageHttpErrors")
    void sendMessageHttpError_sendMessage_throwsMappedBusinessException(HttpStatus status,
                                                                        AiChatErrorCode errorCode) {
        messageServer.expect(requestTo("http://localhost:9000/v1/chat/sessions/101/messages"))
                .andRespond(withStatus(status));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> client.sendMessage(101L, "주말마다 캠핑 가요"));

        assertEquals(errorCode, exception.getErrorCode());
    }

    @Test
    void sessionClosedConflict_sendMessage_throwsSessionClosed() {
        messageServer.expect(requestTo("http://localhost:9000/v1/chat/sessions/101/messages"))
                .andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON).body("""
                        {"code": "SESSION_CLOSED", "message": "이미 끝난 대화입니다.", "retryable": false}
                        """));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> client.sendMessage(101L, "주말마다 캠핑 가요"));

        assertEquals(AiChatErrorCode.AICHAT_SESSION_CLOSED, exception.getErrorCode());
    }

    @Test
    void turnInProgressConflict_sendMessage_throwsTurnInProgress() {
        messageServer.expect(requestTo("http://localhost:9000/v1/chat/sessions/101/messages"))
                .andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON).body("""
                        {"code": "TURN_IN_PROGRESS", "message": "이전 메시지를 처리 중입니다.", "retryable": true}
                        """));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> client.sendMessage(101L, "주말마다 캠핑 가요"));

        assertEquals(AiChatErrorCode.AICHAT_TURN_IN_PROGRESS, exception.getErrorCode());
    }

    // 원인을 모르면 대화방을 만료시키지 않는 쪽으로 처리한다
    @Test
    void conflictWithoutErrorCode_sendMessage_throwsTurnInProgress() {
        messageServer.expect(requestTo("http://localhost:9000/v1/chat/sessions/101/messages"))
                .andRespond(withStatus(HttpStatus.CONFLICT));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> client.sendMessage(101L, "주말마다 캠핑 가요"));

        assertEquals(AiChatErrorCode.AICHAT_TURN_IN_PROGRESS, exception.getErrorCode());
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

    private static Stream<Arguments> sendMessageHttpErrors() {
        return Stream.of(
                Arguments.of(HttpStatus.NOT_FOUND, AiChatErrorCode.AICHAT_SESSION_NOT_FOUND),
                Arguments.of(HttpStatus.TOO_MANY_REQUESTS, AiChatErrorCode.AICHAT_REQUEST_REJECTED),
                Arguments.of(HttpStatus.UNAUTHORIZED, AiChatErrorCode.AICHAT_AUTHENTICATION_FAILED),
                Arguments.of(HttpStatus.BAD_GATEWAY, AiChatErrorCode.AICHAT_SERVER_UNAVAILABLE)
        );
    }

    private static Stream<Arguments> httpErrors() {
        return Stream.of(
                Arguments.of(HttpStatus.UNAUTHORIZED, AiChatErrorCode.AICHAT_AUTHENTICATION_FAILED),
                Arguments.of(HttpStatus.BAD_REQUEST, AiChatErrorCode.AICHAT_REQUEST_REJECTED),
                Arguments.of(HttpStatus.INTERNAL_SERVER_ERROR, AiChatErrorCode.AICHAT_SERVER_UNAVAILABLE)
        );
    }
}
