package kr.ktb.zura.needu.aichat.client;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Stream;
import kr.ktb.zura.needu.aichat.client.dto.request.AiServerAnalysisKeywordsRequest;
import kr.ktb.zura.needu.aichat.client.dto.request.AiServerPatchAnalysisRequest;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerAnalysisKeywordResponse;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerAnalysisKeywordsResponse;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerAnalysisResponse;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerCloseSessionResponse;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerHealthResponse;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerSendMessageResponse;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerSessionMessageResponse;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerSessionResponse;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerStartSessionResponse;
import kr.ktb.zura.needu.aichat.exception.AiChatErrorCode;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.product.type.PlatformType;
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
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
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
                .andExpect(headerDoesNotExist("Authorization"))
                .andRespond(withSuccess("""
                        {"status": "ok", "version": "0.1.0"}
                        """, MediaType.APPLICATION_JSON));

        AiServerHealthResponse response = client.checkHealth();

        assertEquals(new AiServerHealthResponse("ok", "0.1.0"), response);
        server.verify();
    }

    @Test
    void getSession_requestsSessionEndpoint() {
        server.expect(requestTo("http://localhost:9000/v1/chat/sessions/101?userId=1"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer service-token"))
                .andExpect(content().string(""))
                .andRespond(withSuccess("""
                        {
                          "sessionId": 101,
                          "userId": 1,
                          "status": "active",
                          "turn": 1,
                          "maxTurns": 20,
                          "messages": [{
                            "role": "assistant",
                            "content": "안녕하세요",
                            "createdAt": "2026-09-22T14:20:00"
                          }],
                          "inputLocked": false,
                          "canClose": false,
                          "lastActiveAt": "2026-09-22T05:20:00Z"
                        }
                        """, MediaType.APPLICATION_JSON));

        AiServerSessionResponse response = client.getSession(1L, 101L);

        assertEquals(new AiServerSessionResponse(
                101L,
                1L,
                "active",
                1,
                20,
                List.of(new AiServerSessionMessageResponse(
                        "assistant",
                        "안녕하세요",
                        LocalDateTime.of(2026, 9, 22, 14, 20)
                )),
                false,
                false,
                Instant.parse("2026-09-22T05:20:00Z")
        ), response);
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
                        {
                          "sessionId": 101,
                          "greeting": "안녕하세요",
                          "createdAt": "2026-09-23T05:18:00+00:00",
                          "maxTurns": 20
                        }
                        """));

        AiServerStartSessionResponse response = client.startSession(1L, 101L);

        assertEquals(new AiServerStartSessionResponse(
                101L, "안녕하세요", OffsetDateTime.parse("2026-09-23T05:18:00+00:00"), 20), response);
        server.verify();
    }

    @Test
    void sendMessage_requestsMessageEndpointAndParsesReply() {
        messageServer.expect(requestTo("http://localhost:9000/v1/chat/sessions/101/messages"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer service-token"))
                .andExpect(content().json("""
                        {"userId": 1, "message": "주말마다 캠핑 가요"}
                        """))
                .andRespond(withSuccess("""
                        {
                          "reply": "캠핑 좋죠.",
                          "createdAt": "2026-09-23T05:20:00+00:00",
                          "turn": 3,
                          "maxTurns": 20,
                          "canClose": false,
                          "inputLocked": false,
                          "progress": 15
                        }
                        """, MediaType.APPLICATION_JSON));

        AiServerSendMessageResponse response = client.sendMessage(1L, 101L, "주말마다 캠핑 가요");

        assertEquals(new AiServerSendMessageResponse(
                "캠핑 좋죠.", OffsetDateTime.parse("2026-09-23T05:20:00+00:00"),
                3, 20, false, false, 15), response);
        messageServer.verify();
    }

    @Test
    void createAnalysis_requestsAnalysisEndpointAndParsesResponse() {
        server.expect(requestTo("http://localhost:9000/v1/chat/sessions/101/analysis"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer service-token"))
                .andRespond(withSuccess("""
                        {
                          "profile": {
                            "userId": 10293,
                            "summary": "캠핑과 핸드드립을 즐깁니다.",
                            "keywords": {
                              "taste": [
                                {"value": "핸드드립", "score": 0.92},
                                {"value": "가벼운 장비", "score": 0.81}
                              ],
                              "interest": [
                                {"value": "캠핑", "score": 0.95},
                                {"value": "티타늄 머그컵", "score": 0.76}
                              ]
                            },
                            "correctionAvailable": true
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        AiServerAnalysisResponse response = client.createAnalysis(101L);

        assertEquals(10293L, response.profile().userId());
        assertEquals("캠핑과 핸드드립을 즐깁니다.", response.profile().summary());
        assertEquals(new AiServerAnalysisKeywordsResponse(
                List.of(
                        new AiServerAnalysisKeywordResponse("핸드드립", 0.92),
                        new AiServerAnalysisKeywordResponse("가벼운 장비", 0.81)),
                List.of(
                        new AiServerAnalysisKeywordResponse("캠핑", 0.95),
                        new AiServerAnalysisKeywordResponse("티타늄 머그컵", 0.76))
        ), response.profile().keywords());
        assertEquals(true, response.profile().correctionAvailable());
        server.verify();
    }

    @Test
    void patchAnalyze_sendsSummaryAndKeywords() {
        server.expect(requestTo("http://localhost:9000/v1/chat/sessions/101/analysis"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header("Authorization", "Bearer service-token"))
                .andExpect(content().json("""
                        {
                          "userId": 10293,
                          "summary": "러닝을 즐깁니다.",
                          "keywords": {"taste": ["러닝"], "interest": ["운동"]}
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "profile": {
                            "userId": 10293,
                            "summary": "러닝을 즐깁니다.",
                            "keywords": {
                              "taste": [{"value": "러닝", "score": 0.9}],
                              "interest": [{"value": "운동", "score": 0.8}]
                            },
                            "correctionAvailable": false
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        AiServerAnalysisResponse response = client.patchAnalyze(101L,
                new AiServerPatchAnalysisRequest(
                        10293L,
                        "러닝을 즐깁니다.",
                        new AiServerAnalysisKeywordsRequest(List.of("러닝"), List.of("운동"))));

        assertEquals("러닝을 즐깁니다.", response.profile().summary());
        server.verify();
    }

    @Test
    void confirmAnalysis_closesSession() {
        server.expect(requestTo("http://localhost:9000/v1/chat/sessions/101/close"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer service-token"))
                .andExpect(content().json("""
                        {
                          "userId": 10293
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "conversationId": 101,
                          "userId": 10293,
                          "summary": "캠핑을 즐깁니다.",
                          "keywords": {"taste": [], "interest": ["캠핑"]},
                          "recommendations": {
                            "generatedAt": "2026-09-23T07:10:00+00:00",
                            "self": {
                              "items": [{
                                "platform": "coupang",
                                "externalId": "88213",
                                "score": 9.2,
                                "reason": "나를 위한 추천"
                              }]
                            },
                            "gift": {
                              "generatedAt": "2026-09-23T07:10:00+00:00",
                              "items": [{
                                "platform": "coupang",
                                "externalId": "88214",
                                "score": 8.0,
                                "reason": "선물 추천"
                              }]
                            }
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        AiServerCloseSessionResponse response = client.confirmAnalysis(10293L, 101L);

        assertEquals(101L, response.conversationId());
        assertEquals(PlatformType.COUPANG,
                response.recommendations().self().items().getFirst().platform());
        assertEquals(new java.math.BigDecimal("9.2"),
                response.recommendations().self().items().getFirst().score());
        assertEquals("88214", response.recommendations().gift().items().getFirst().externalId());
        server.verify();
    }

    @ParameterizedTest
    @MethodSource("sendMessageHttpErrors")
    void sendMessageHttpError_sendMessage_throwsMappedBusinessException(HttpStatus status,
                                                                        AiChatErrorCode errorCode) {
        messageServer.expect(requestTo("http://localhost:9000/v1/chat/sessions/101/messages"))
                .andRespond(withStatus(status));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> client.sendMessage(1L, 101L, "주말마다 캠핑 가요"));

        assertEquals(errorCode, exception.getErrorCode());
    }

    @Test
    void sessionClosedConflict_sendMessage_throwsSessionClosed() {
        messageServer.expect(requestTo("http://localhost:9000/v1/chat/sessions/101/messages"))
                .andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON).body("""
                        {"code": "SESSION_CLOSED", "message": "이미 끝난 대화입니다.", "retryable": false}
                        """));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> client.sendMessage(1L, 101L, "주말마다 캠핑 가요"));

        assertEquals(AiChatErrorCode.AICHAT_SESSION_CLOSED, exception.getErrorCode());
    }

    @Test
    void turnInProgressConflict_sendMessage_throwsTurnInProgress() {
        messageServer.expect(requestTo("http://localhost:9000/v1/chat/sessions/101/messages"))
                .andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON).body("""
                        {"code": "TURN_IN_PROGRESS", "message": "이전 메시지를 처리 중입니다.", "retryable": true}
                        """));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> client.sendMessage(1L, 101L, "주말마다 캠핑 가요"));

        assertEquals(AiChatErrorCode.AICHAT_TURN_IN_PROGRESS, exception.getErrorCode());
    }

    // 원인을 모르면 대화방을 만료시키지 않는 쪽으로 처리한다
    @Test
    void conflictWithoutErrorCode_sendMessage_throwsTurnInProgress() {
        messageServer.expect(requestTo("http://localhost:9000/v1/chat/sessions/101/messages"))
                .andRespond(withStatus(HttpStatus.CONFLICT));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> client.sendMessage(1L, 101L, "주말마다 캠핑 가요"));

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
