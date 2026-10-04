package kr.ktb.zura.needu.common.openapi;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import kr.ktb.zura.needu.common.ratelimit.RateLimitProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import static org.assertj.core.api.Assertions.assertThat;

class CommonErrorResponseCustomizerTest {

    private static final String LIMITED_PATH = "/api/v1/friends/{userId}";
    private static final String PUBLIC_PATH = "/api/v1/auth/refresh";

    private final CommonErrorResponseCustomizer customizer = new CommonErrorResponseCustomizer(
            new RateLimitProperties(Map.of("friend-detail",
                    new RateLimitProperties.Policy(HttpMethod.GET, LIMITED_PATH, 120, Duration.ofMinutes(1), 100))));

    @Test
    void authenticatedRateLimitedGet_addsUnauthorizedTooManyRequestsAndServerError() {
        Operation operation = newOperation();
        OpenAPI openApi = new OpenAPI().paths(new Paths().addPathItem(LIMITED_PATH, new PathItem().get(operation)));

        customizer.customise(openApi);

        assertThat(operation.getResponses().keySet()).containsExactly("401", "429", "500");
    }

    @Test
    void publicPost_addsCsrfForbiddenWithoutUnauthorized() {
        Operation operation = newOperation().security(List.of());
        OpenAPI openApi = new OpenAPI().paths(new Paths().addPathItem(PUBLIC_PATH, new PathItem().post(operation)));

        customizer.customise(openApi);

        assertThat(operation.getResponses().keySet()).containsExactly("403", "500");
        assertThat(openApi.getComponents().getSchemas()).containsKey(ErrorResponseDocs.SCHEMA_NAME);
    }

    private Operation newOperation() {
        return new Operation().responses(new ApiResponses());
    }
}
