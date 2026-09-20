package kr.ktb.zura.needu.aichat.client;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.Map;
import kr.ktb.zura.needu.aichat.dto.request.AiServerStartSessionRequest;
import kr.ktb.zura.needu.aichat.dto.response.AiServerStartSessionResponse;
import kr.ktb.zura.needu.aichat.dto.response.HealthResponse;
import kr.ktb.zura.needu.aichat.exception.AiChatErrorCode;
import kr.ktb.zura.needu.aichat.type.AiChatEndpoint;
import kr.ktb.zura.needu.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class AiChatClient {

    private final RestClient restClient;
    private final String serviceToken;

    public AiChatClient(RestClient restClient,
                        @Value("${AI_SERVICE_TOKEN}") String serviceToken) {
        this.restClient = restClient;
        this.serviceToken = serviceToken;
    }

    public HealthResponse checkHealth() {
        return request(AiChatEndpoint.CHECK_HEALTH, Map.of(), null, HealthResponse.class);
    }

    public AiServerStartSessionResponse startSession(Long userId, Long conversationRoomId) {
        return request(AiChatEndpoint.START_SESSION, Map.of(),
                new AiServerStartSessionRequest(userId, conversationRoomId), AiServerStartSessionResponse.class);
    }

    public void sendMessage() {

    }

    public void confirmTasteProfile() {

    }

    private <T> T request(AiChatEndpoint endpoint,
                          Map<String, ?> pathVariables,
                          Object requestBody,
                          Class<T> responseType) {
        try {
            RestClient.RequestBodySpec request = restClient.method(endpoint.getHttpMethod())
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
            int status = exception.getStatusCode().value();
            if (status == 401 || status == 403) {
                throw new BusinessException(AiChatErrorCode.AICHAT_AUTHENTICATION_FAILED);
            }
            if (exception.getStatusCode().is5xxServerError()) {
                throw new BusinessException(AiChatErrorCode.AICHAT_SERVER_UNAVAILABLE);
            }
            throw new BusinessException(AiChatErrorCode.AICHAT_REQUEST_REJECTED);
        } catch (ResourceAccessException exception) {
            throw new BusinessException(isTimeout(exception)
                    ? AiChatErrorCode.AICHAT_REQUEST_TIMEOUT
                    : AiChatErrorCode.AICHAT_SERVER_UNAVAILABLE);
        } catch (RestClientException exception) {
            throw new BusinessException(AiChatErrorCode.AICHAT_INVALID_RESPONSE);
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
