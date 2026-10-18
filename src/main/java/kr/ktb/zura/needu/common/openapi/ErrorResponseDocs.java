package kr.ktb.zura.needu.common.openapi;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.util.LinkedHashMap;
import java.util.Map;
import kr.ktb.zura.needu.common.exception.ErrorCode;

final class ErrorResponseDocs {

    static final String SCHEMA_NAME = "ErrorResponse";

    private static final String SCHEMA_REF = "#/components/schemas/" + SCHEMA_NAME;
    private static final String JSON = "application/json";

    private ErrorResponseDocs() {
    }

    static Schema<?> errorResponseSchema() {
        return new ObjectSchema()
                .addProperty("message", new StringSchema().description("사용자에게 보여줄 오류 메시지"))
                .addProperty("data", new ObjectSchema().description("추가 정보. 없으면 null"));
    }

    // 같은 상태 코드의 오류는 하나의 응답에 예시로 모은다. 에러 코드는 응답 본문에 없지만 예시 구분용 이름으로 쓴다.
    // 문구가 같은 오류는 FE 입장에서 구분되지 않으므로 하나만 남긴다.
    static void addError(Operation operation, ErrorCode errorCode, Object data) {
        if (operation.getResponses() == null) {
            operation.setResponses(new ApiResponses());
        }
        String status = String.valueOf(errorCode.getStatus().value());
        ApiResponse response = operation.getResponses().computeIfAbsent(status, key -> new ApiResponse()
                .description(errorCode.getStatus().getReasonPhrase())
                .content(new Content().addMediaType(JSON,
                        new MediaType().schema(new Schema<>().$ref(SCHEMA_REF)))));
        MediaType mediaType = response.getContent().get(JSON);
        boolean isDuplicatedMessage = mediaType.getExamples() != null && mediaType.getExamples().values().stream()
                .anyMatch(example -> errorCode.getMessage().equals(example.getSummary()));
        if (isDuplicatedMessage) {
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", errorCode.getMessage());
        body.put("data", data);
        mediaType.addExamples(errorCode.name(), new Example().summary(errorCode.getMessage()).value(body));
    }
}
