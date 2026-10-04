package kr.ktb.zura.needu.common.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import kr.ktb.zura.needu.common.exception.CommonErrorCode;
import kr.ktb.zura.needu.common.ratelimit.RateLimitProperties;
import kr.ktb.zura.needu.common.response.CsrfRefreshResponse;
import kr.ktb.zura.needu.common.response.RetryAfterResponse;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.stereotype.Component;

// 경로와 HTTP 메서드로 정해지는 공통 오류(인증, CSRF, 요청 횟수 제한, 서버 오류)를 모든 API 문서에 추가한다.
// 필터 단계에서 응답하는 오류라 컨트롤러 메서드만 봐서는 알 수 없다.
@Component
@RequiredArgsConstructor
public class CommonErrorResponseCustomizer implements OpenApiCustomizer {

    private static final Set<PathItem.HttpMethod> CSRF_PROTECTED_METHODS = Set.of(
            PathItem.HttpMethod.POST, PathItem.HttpMethod.PUT, PathItem.HttpMethod.PATCH, PathItem.HttpMethod.DELETE);

    private final RateLimitProperties rateLimitProperties;

    @Override
    public void customise(OpenAPI openApi) {
        if (openApi.getComponents() == null) {
            openApi.setComponents(new Components());
        }
        openApi.getComponents().addSchemas(ErrorResponseDocs.SCHEMA_NAME, ErrorResponseDocs.errorResponseSchema());
        if (openApi.getPaths() == null) {
            return;
        }
        openApi.getPaths().forEach((path, pathItem) -> pathItem.readOperationsMap()
                .forEach((method, operation) -> addCommonErrors(path, method, operation)));
    }

    private void addCommonErrors(String path, PathItem.HttpMethod method, Operation operation) {
        // *ControllerDocs에서 publicApi()로 등록한 API(SecurityConfig의 permitAll)는 보안 요구사항이 비어 있다.
        if (operation.getSecurity() == null || !operation.getSecurity().isEmpty()) {
            ErrorResponseDocs.addError(operation, CommonErrorCode.COMMON_UNAUTHORIZED, null);
        }
        if (CSRF_PROTECTED_METHODS.contains(method)) {
            ErrorResponseDocs.addError(operation, CommonErrorCode.COMMON_CSRF_TOKEN_INVALID,
                    new CsrfRefreshResponse(true));
        }
        rateLimitProperties.policies().values().stream()
                .filter(policy -> policy.method().name().equals(method.name())
                        && policy.pathPattern().equals(path))
                .findFirst()
                .ifPresent(policy -> ErrorResponseDocs.addError(operation, CommonErrorCode.COMMON_TOO_MANY_REQUESTS,
                        new RetryAfterResponse(policy.window().toSeconds())));
        ErrorResponseDocs.addError(operation, CommonErrorCode.COMMON_INTERNAL_SERVER_ERROR, null);
        sortByStatus(operation);
    }

    private void sortByStatus(Operation operation) {
        Map<String, ApiResponse> sorted = new TreeMap<>(operation.getResponses());
        ApiResponses responses = new ApiResponses();
        responses.putAll(sorted);
        operation.setResponses(responses);
    }
}
