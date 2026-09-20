package kr.ktb.zura.needu.aichat.client;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.Map;
import kr.ktb.zura.needu.aichat.dto.request.AiServerSendMessageRequest;
import kr.ktb.zura.needu.aichat.dto.request.AiServerStartSessionRequest;
import kr.ktb.zura.needu.aichat.dto.response.AiServerErrorResponse;
import kr.ktb.zura.needu.aichat.dto.response.AiServerSendMessageResponse;
import kr.ktb.zura.needu.aichat.dto.response.AiServerStartSessionResponse;
import kr.ktb.zura.needu.aichat.dto.response.HealthResponse;
import kr.ktb.zura.needu.aichat.exception.AiChatErrorCode;
import kr.ktb.zura.needu.aichat.type.AiChatEndpoint;
import kr.ktb.zura.needu.common.exception.BusinessException;
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

    private static final String CONVERSATION_ROOM_ID = "conversationRoomId";
    private static final String SESSION_CLOSED_CODE = "SESSION_CLOSED";

    private final RestClient restClient;
    private final RestClient messageRestClient;
    private final String serviceToken;

    public AiChatClient(@Qualifier("aiChatRestClient") RestClient restClient,
                        @Qualifier("aiChatMessageRestClient") RestClient messageRestClient,
                        @Value("${AI_SERVICE_TOKEN}") String serviceToken) {
        this.restClient = restClient;
        this.messageRestClient = messageRestClient;
        this.serviceToken = serviceToken;
    }

    public HealthResponse checkHealth() {
        return request(AiChatEndpoint.CHECK_HEALTH, Map.of(), null, HealthResponse.class);
    }

    public AiServerStartSessionResponse startSession(Long userId, Long conversationRoomId) {
        return request(AiChatEndpoint.START_SESSION, Map.of(),
                new AiServerStartSessionRequest(userId, conversationRoomId), AiServerStartSessionResponse.class);
    }

    public AiServerSendMessageResponse sendMessage(Long conversationRoomId, String message) {
        return request(AiChatEndpoint.SEND_MESSAGE, Map.of(CONVERSATION_ROOM_ID, conversationRoomId),
                new AiServerSendMessageRequest(message), AiServerSendMessageResponse.class);
    }

    public void confirmTasteProfile() {

    }

    private <T> T request(AiChatEndpoint endpoint,
                          Map<String, ?> pathVariables,
                          Object requestBody,
                          Class<T> responseType) {
        try {
            RestClient.RequestBodySpec request = clientFor(endpoint).method(endpoint.getHttpMethod())
                    .uri(endpoint.getUrl(), pathVariables)
                    .headers(headers -> headers.setBearerAuth(serviceToken));

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
