package kr.ktb.zura.needu.aichat.client;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import kr.ktb.zura.needu.aichat.client.dto.request.AiServerCloseSessionRequest;
import kr.ktb.zura.needu.aichat.client.dto.request.AiServerPatchAnalysisRequest;
import kr.ktb.zura.needu.aichat.client.dto.request.AiServerSendMessageRequest;
import kr.ktb.zura.needu.aichat.client.dto.request.AiServerStartSessionRequest;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerAnalysisResponse;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerAnalysisKeywordResponse;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerAnalysisKeywordsResponse;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerAnalysisProfileResponse;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerCloseSessionResponse;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerCloseSessionKeywordsResponse;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerErrorResponse;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerHealthResponse;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerRecommendationResult;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerRecommendedItem;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerRecommendationsResponse;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerSendMessageResponse;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerStartSessionResponse;
import kr.ktb.zura.needu.aichat.exception.AiChatErrorCode;
import kr.ktb.zura.needu.aichat.type.AiChatEndpoint;
import kr.ktb.zura.needu.aichat.type.AiChatRequestField;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.product.type.PlatformType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class AiChatClient {

    private static final String SESSION_CLOSED_CODE = "SESSION_CLOSED";
    private static final String MOCK_SELF_PRODUCT_ID = "88213";
    private static final String MOCK_GIFT_PRODUCT_ID = "88214";

    private final RestClient restClient;
    private final RestClient messageRestClient;
    private final String serviceToken;
    private final boolean mockEnabled;
    private final Map<Long, Integer> mockTurns = new ConcurrentHashMap<>();
    private final Map<Long, AiServerAnalysisResponse> mockAnalyses = new ConcurrentHashMap<>();

    public AiChatClient(@Qualifier("aiChatRestClient") RestClient restClient,
                        @Qualifier("aiChatMessageRestClient") RestClient messageRestClient,
                        @Value("${AI_SERVICE_TOKEN:}") String serviceToken,
                        @Value("${ai.server.mock-enabled:false}") boolean mockEnabled) {
        this.restClient = restClient;
        this.messageRestClient = messageRestClient;
        this.serviceToken = serviceToken;
        this.mockEnabled = mockEnabled;
    }

    public AiServerHealthResponse checkHealth() {
        return request(AiChatEndpoint.CHECK_HEALTH, Map.of(), null, AiServerHealthResponse.class);
    }

    public AiServerStartSessionResponse startSession(Long userId, Long conversationRoomId) {
        if (mockEnabled) {
            mockTurns.put(conversationRoomId, 0);
            return new AiServerStartSessionResponse(
                    conversationRoomId,
                    "안녕하세요! 요즘 어떻게 지내시는지 궁금해요.",
                    OffsetDateTime.now(ZoneOffset.UTC),
                    OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(30),
                    20);
        }
        return request(AiChatEndpoint.START_SESSION, Map.of(),
                new AiServerStartSessionRequest(userId, conversationRoomId), AiServerStartSessionResponse.class);
    }

    public AiServerSendMessageResponse sendMessage(Long userId, Long conversationRoomId, String message) {
        if (mockEnabled) {
            int turn = mockTurns.merge(conversationRoomId, 1, Integer::sum);
            boolean inputLocked = turn >= 2;
            return new AiServerSendMessageResponse(
                    inputLocked
                            ? "좋아요. 말씀해주신 내용을 바탕으로 취향을 분석해 볼게요."
                            : "흥미롭네요. 그 활동에서 가장 좋아하는 점은 무엇인가요?",
                    OffsetDateTime.now(ZoneOffset.UTC),
                    OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(30),
                    turn,
                    20,
                    inputLocked,
                    inputLocked,
                    inputLocked ? 100 : 50);
        }
        return request(AiChatEndpoint.SEND_MESSAGE, pathVariables(conversationRoomId),
                new AiServerSendMessageRequest(userId, message), AiServerSendMessageResponse.class);
    }

    public AiServerAnalysisResponse createAnalysis(Long conversationRoomId) {
        if (mockEnabled) {
            AiServerAnalysisResponse response = new AiServerAnalysisResponse(new AiServerAnalysisProfileResponse(
                    10293L,
                    "캠핑과 실용적인 장비를 좋아합니다.",
                    new AiServerAnalysisKeywordsResponse(
                            List.of(new AiServerAnalysisKeywordResponse("실용적인 장비", 0.92)),
                            List.of(new AiServerAnalysisKeywordResponse("캠핑", 0.95))),
                    true));
            mockAnalyses.put(conversationRoomId, response);
            return response;
        }
        return request(AiChatEndpoint.CREATE_ANALYSIS, pathVariables(conversationRoomId),
                null, AiServerAnalysisResponse.class);
    }

    public AiServerAnalysisResponse patchAnalyze(Long conversationId, AiServerPatchAnalysisRequest request) {
        if (mockEnabled) {
            AiServerAnalysisResponse response = new AiServerAnalysisResponse(new AiServerAnalysisProfileResponse(
                    request.userId(),
                    request.summary(),
                    new AiServerAnalysisKeywordsResponse(
                            toMockKeywords(request.keywords().taste()),
                            toMockKeywords(request.keywords().interest())),
                    false));
            mockAnalyses.put(conversationId, response);
            return response;
        }
        return request(AiChatEndpoint.PATCH_ANALYZE, pathVariables(conversationId),
                request, AiServerAnalysisResponse.class);
    }

    public AiServerCloseSessionResponse confirmAnalysis(
            Long userId, Long conversationRoomId) {
        if (mockEnabled) {
            AiServerAnalysisProfileResponse profile = mockAnalyses.getOrDefault(
                    conversationRoomId, createMockAnalysis(userId)).profile();
            mockAnalyses.remove(conversationRoomId);
            mockTurns.remove(conversationRoomId);
            return new AiServerCloseSessionResponse(
                    conversationRoomId,
                    userId,
                    profile.summary(),
                    new AiServerCloseSessionKeywordsResponse(
                            toKeywordValues(profile.keywords().taste()),
                            toKeywordValues(profile.keywords().interest())),
                    new AiServerRecommendationsResponse(
                            new AiServerRecommendationResult(List.of(new AiServerRecommendedItem(
                                    PlatformType.COUPANG,
                                    MOCK_SELF_PRODUCT_ID,
                                    new BigDecimal("9.2"),
                                    "캠핑 취향과 잘 맞는 상품이에요."))),
                            new AiServerRecommendationResult(List.of(new AiServerRecommendedItem(
                                    PlatformType.COUPANG,
                                    MOCK_GIFT_PRODUCT_ID,
                                    new BigDecimal("8.0"),
                                    "캠핑을 좋아하는 분에게 선물하기 좋은 상품이에요.")))));
        }
        return request(AiChatEndpoint.CONFIRM_ANALYSIS, pathVariables(conversationRoomId),
                new AiServerCloseSessionRequest(userId), AiServerCloseSessionResponse.class);
    }

    private AiServerAnalysisResponse createMockAnalysis(Long userId) {
        return new AiServerAnalysisResponse(new AiServerAnalysisProfileResponse(
                userId,
                "캠핑과 실용적인 장비를 좋아합니다.",
                new AiServerAnalysisKeywordsResponse(
                        List.of(new AiServerAnalysisKeywordResponse("실용적인 장비", 0.92)),
                        List.of(new AiServerAnalysisKeywordResponse("캠핑", 0.95))),
                true));
    }

    private List<AiServerAnalysisKeywordResponse> toMockKeywords(List<String> keywords) {
        return keywords.stream()
                .map(keyword -> new AiServerAnalysisKeywordResponse(keyword, 1.0))
                .toList();
    }

    private List<String> toKeywordValues(List<AiServerAnalysisKeywordResponse> keywords) {
        return keywords.stream().map(AiServerAnalysisKeywordResponse::value).toList();
    }

    private Map<String, Long> pathVariables(Long conversationRoomId) {
        return Map.of(AiChatRequestField.CONVERSATION_ROOM_ID.getFieldName(), conversationRoomId);
    }

    private <T> T request(AiChatEndpoint endpoint,
                          Map<String, ?> pathVariables,
                          Object requestBody,
                          Class<T> responseType) {
        try {
            RestClient.RequestBodySpec request = clientFor(endpoint).method(endpoint.getHttpMethod())
                    .uri(endpoint.getUrl(), pathVariables);

            if (endpoint != AiChatEndpoint.CHECK_HEALTH) {
                if (serviceToken.isBlank()) {
                    throw new BusinessException(AiChatErrorCode.AICHAT_AUTHENTICATION_FAILED);
                }
                request.headers(headers -> headers.setBearerAuth(serviceToken));
            }

            if (requestBody != null) {
                request.body(requestBody);
            }

            T response = request.retrieve().body(responseType);
            if (response == null && responseType != Void.class) {
                throw new BusinessException(AiChatErrorCode.AICHAT_INVALID_RESPONSE);
            }
            return response;
        } catch (RestClientResponseException exception) {
            throw new BusinessException(toErrorCode(endpoint, exception));
        } catch (ResourceAccessException exception) {
            throw new BusinessException(isTimeout(exception)
                    ? AiChatErrorCode.AICHAT_REQUEST_TIMEOUT
                    : AiChatErrorCode.AICHAT_SERVER_UNAVAILABLE);
        } catch (RestClientException exception) {
            throw new BusinessException(AiChatErrorCode.AICHAT_INVALID_RESPONSE);
        }
    }

    private RestClient clientFor(AiChatEndpoint endpoint) {
        return endpoint == AiChatEndpoint.SEND_MESSAGE ? messageRestClient : restClient;
    }

    private AiChatErrorCode toErrorCode(AiChatEndpoint endpoint, RestClientResponseException exception) {
        int status = exception.getStatusCode().value();
        if (status == HttpStatus.UNAUTHORIZED.value() || status == HttpStatus.FORBIDDEN.value()) {
            return AiChatErrorCode.AICHAT_AUTHENTICATION_FAILED;
        }
        if (exception.getStatusCode().is5xxServerError()) {
            return AiChatErrorCode.AICHAT_SERVER_UNAVAILABLE;
        }
        // 메시지 전송에서만 세션 소멸(404)과 종료된 대화(409)를 구분해 대화방을 정리할 수 있게 한다
        if (endpoint != AiChatEndpoint.SEND_MESSAGE) {
            return AiChatErrorCode.AICHAT_REQUEST_REJECTED;
        }
        if (status == HttpStatus.NOT_FOUND.value()) {
            return AiChatErrorCode.AICHAT_SESSION_NOT_FOUND;
        }
        if (status == HttpStatus.CONFLICT.value()) {
            return toConflictErrorCode(exception);
        }
        return AiChatErrorCode.AICHAT_REQUEST_REJECTED;
    }

    // 409에는 끝난 대화(SESSION_CLOSED)와 처리 중(TURN_IN_PROGRESS)이 섞여 있어 오류 코드로 구분한다.
    // 구분하지 못하면 대화방을 만료시키지 않는 쪽으로 본다. 살아있는 대화를 지우는 것이 더 큰 손해다.
    private AiChatErrorCode toConflictErrorCode(RestClientResponseException exception) {
        AiServerErrorResponse error = readError(exception);
        return error != null && SESSION_CLOSED_CODE.equals(error.code())
                ? AiChatErrorCode.AICHAT_SESSION_CLOSED
                : AiChatErrorCode.AICHAT_TURN_IN_PROGRESS;
    }

    private AiServerErrorResponse readError(RestClientResponseException exception) {
        try {
            return exception.getResponseBodyAs(AiServerErrorResponse.class);
        } catch (RestClientException e) {
            return null;
        }
    }

    private boolean isTimeout(Throwable throwable) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (cause instanceof HttpTimeoutException || cause instanceof SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }
}
