package kr.ktb.zura.needu.aichat.client;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import jakarta.annotation.PreDestroy;
import kr.ktb.zura.needu.aichat.client.dto.request.AiServerCloseSessionRequest;
import kr.ktb.zura.needu.aichat.client.dto.request.AiServerPatchAnalysisRequest;
import kr.ktb.zura.needu.aichat.client.dto.request.AiServerSendMessageRequest;
import kr.ktb.zura.needu.aichat.client.dto.request.AiServerSendMessageStreamRequest;
import kr.ktb.zura.needu.aichat.client.dto.request.AiServerStartSessionRequest;
import kr.ktb.zura.needu.aichat.client.dto.request.AiServerUserProfileRequest;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerAnalysisResponse;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerCloseSessionResponse;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerErrorResponse;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerHealthResponse;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerSendMessageResponse;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerStartSessionResponse;
import kr.ktb.zura.needu.aichat.exception.AiChatErrorCode;
import kr.ktb.zura.needu.aichat.type.AiChatEndpoint;
import kr.ktb.zura.needu.aichat.type.AiChatRequestField;
import kr.ktb.zura.needu.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.json.JsonMapper;

@Component
@ConditionalOnProperty(name = "ai.server.mock-enabled", havingValue = "false", matchIfMissing = true)
public class HttpAiChatClient implements AiChatClient {

    private static final String SESSION_CLOSED_CODE = "SESSION_CLOSED";
    private static final String PROFILE_TOO_SPARSE_CODE = "PROFILE_TOO_SPARSE";

    private final RestClient restClient;
    private final RestClient messageRestClient;
    private final String serviceToken;
    private final AiServerEventStreamReader streamReader;

    public HttpAiChatClient(@Qualifier("aiChatRestClient") RestClient restClient,
                            @Qualifier("aiChatMessageRestClient") RestClient messageRestClient,
                            @Value("${AI_SERVICE_TOKEN:}") String serviceToken,
                            JsonMapper jsonMapper,
                            @Value("${ai.server.stream-idle-timeout}") Duration streamIdleTimeout,
                            @Value("${ai.server.message-read-timeout}") Duration streamTotalTimeout) {
        this.restClient = restClient;
        this.messageRestClient = messageRestClient;
        this.serviceToken = serviceToken;
        this.streamReader = new AiServerEventStreamReader(jsonMapper, streamIdleTimeout, streamTotalTimeout);
    }

    @PreDestroy
    void closeStreamReader() {
        streamReader.close();
    }

    public AiServerHealthResponse checkHealth() {
        return request(AiChatEndpoint.CHECK_HEALTH, Map.of(), null, AiServerHealthResponse.class);
    }

    @Override
    public AiServerStartSessionResponse startSession(Long userId, Long conversationRoomId,
                                                     AiServerUserProfileRequest userProfile) {
        return request(AiChatEndpoint.START_SESSION, Map.of(),
                new AiServerStartSessionRequest(userId, conversationRoomId, userProfile),
                AiServerStartSessionResponse.class);
    }

    @Override
    public AiServerSendMessageResponse sendMessage(Long userId, Long conversationRoomId, String message) {
        return request(AiChatEndpoint.SEND_MESSAGE, pathVariables(conversationRoomId),
                new AiServerSendMessageRequest(userId, message), AiServerSendMessageResponse.class);
    }

    @Override
    public AiServerSendMessageResponse streamMessage(Long userId, Long conversationRoomId, Long userMessageId,
                                                     String message, Consumer<String> onDelta) {
        AiChatEndpoint endpoint = AiChatEndpoint.SEND_MESSAGE_STREAM;
        if (serviceToken.isBlank()) {
            throw new BusinessException(AiChatErrorCode.AICHAT_AUTHENTICATION_FAILED);
        }
        try {
            return messageRestClient.method(endpoint.getHttpMethod())
                    .uri(endpoint.getUrl(), pathVariables(conversationRoomId))
                    .headers(headers -> {
                        headers.setBearerAuth(serviceToken);
                        headers.setAccept(List.of(MediaType.TEXT_EVENT_STREAM));
                    })
                    .body(new AiServerSendMessageStreamRequest(userId, userMessageId, message))
                    .exchange((request, response) -> {
                        if (response.getStatusCode().isError()) {
                            throw new BusinessException(toErrorCode(
                                    endpoint, response.getStatusCode(), () -> readError(response)));
                        }
                        return streamReader.read(response.getBody(), onDelta);
                    });
        } catch (ResourceAccessException exception) {
            throw new BusinessException(isTimeout(exception)
                    ? AiChatErrorCode.AICHAT_REQUEST_TIMEOUT
                    : AiChatErrorCode.AICHAT_SERVER_UNAVAILABLE);
        } catch (RestClientException exception) {
            throw new BusinessException(AiChatErrorCode.AICHAT_INVALID_RESPONSE);
        }
    }

    @Override
    public AiServerAnalysisResponse createAnalysis(Long conversationRoomId) {
        return request(AiChatEndpoint.CREATE_ANALYSIS, pathVariables(conversationRoomId),
                null, AiServerAnalysisResponse.class);
    }

    @Override
    public AiServerAnalysisResponse patchAnalyze(Long conversationRoomId, AiServerPatchAnalysisRequest request) {
        return request(AiChatEndpoint.PATCH_ANALYZE, pathVariables(conversationRoomId),
                request, AiServerAnalysisResponse.class);
    }

    @Override
    public AiServerCloseSessionResponse confirmAnalysis(Long userId, Long conversationRoomId) {
        return request(AiChatEndpoint.CONFIRM_ANALYSIS, pathVariables(conversationRoomId),
                new AiServerCloseSessionRequest(userId), AiServerCloseSessionResponse.class);
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
        return isMessageEndpoint(endpoint) ? messageRestClient : restClient;
    }

    private boolean isMessageEndpoint(AiChatEndpoint endpoint) {
        return endpoint == AiChatEndpoint.SEND_MESSAGE || endpoint == AiChatEndpoint.SEND_MESSAGE_STREAM;
    }

    private AiChatErrorCode toErrorCode(AiChatEndpoint endpoint, RestClientResponseException exception) {
        return toErrorCode(endpoint, exception.getStatusCode(), () -> readError(exception));
    }

    private AiChatErrorCode toErrorCode(AiChatEndpoint endpoint, HttpStatusCode statusCode,
                                        Supplier<AiServerErrorResponse> errorReader) {
        int status = statusCode.value();
        if (status == HttpStatus.UNAUTHORIZED.value() || status == HttpStatus.FORBIDDEN.value()) {
            return AiChatErrorCode.AICHAT_AUTHENTICATION_FAILED;
        }
        if (statusCode.is5xxServerError()) {
            return AiChatErrorCode.AICHAT_SERVER_UNAVAILABLE;
        }
        if (endpoint == AiChatEndpoint.CREATE_ANALYSIS && status == HttpStatus.UNPROCESSABLE_CONTENT.value()) {
            return toUnprocessableAnalysisErrorCode(errorReader.get());
        }
        if (!isMessageEndpoint(endpoint)) {
            return AiChatErrorCode.AICHAT_REQUEST_REJECTED;
        }
        if (status == HttpStatus.NOT_FOUND.value()) {
            return AiChatErrorCode.AICHAT_SESSION_NOT_FOUND;
        }
        if (status == HttpStatus.CONFLICT.value()) {
            return toConflictErrorCode(errorReader.get());
        }
        return AiChatErrorCode.AICHAT_REQUEST_REJECTED;
    }

    // 409에는 끝난 대화(SESSION_CLOSED)와 처리 중(TURN_IN_PROGRESS)이 섞여 있어 오류 코드로 구분한다.
    // 구분하지 못하면 대화방을 만료시키지 않는 쪽으로 본다. 살아있는 대화를 지우는 것이 더 큰 손해다.
    private AiChatErrorCode toConflictErrorCode(AiServerErrorResponse error) {
        return error != null && SESSION_CLOSED_CODE.equals(error.code())
                ? AiChatErrorCode.AICHAT_SESSION_CLOSED
                : AiChatErrorCode.AICHAT_TURN_IN_PROGRESS;
    }

    private AiChatErrorCode toUnprocessableAnalysisErrorCode(AiServerErrorResponse error) {

        // 취향 부족으로 인한 AI 서버의 422 응답일 경우
        if (error != null && PROFILE_TOO_SPARSE_CODE.equals(error.code())) {
            return AiChatErrorCode.AICHAT_PROFILE_TOO_SPARSE;
        }

        return AiChatErrorCode.AICHAT_REQUEST_REJECTED;
    }

    private AiServerErrorResponse readError(RestClientResponseException exception) {
        try {
            return exception.getResponseBodyAs(AiServerErrorResponse.class);
        } catch (RestClientException e) {
            return null;
        }
    }

    private AiServerErrorResponse readError(RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response) {
        try {
            return response.bodyTo(AiServerErrorResponse.class);
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
