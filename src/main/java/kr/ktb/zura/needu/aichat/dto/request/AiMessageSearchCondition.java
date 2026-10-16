package kr.ktb.zura.needu.aichat.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record AiMessageSearchCondition(
        String cursor,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @Min(AiMessageSearchCondition.MIN_PAGE_SIZE)
        @Max(AiMessageSearchCondition.MAX_PAGE_SIZE)
        int size
) {

    private static final int MIN_PAGE_SIZE = 1;
    private static final int MAX_PAGE_SIZE = 50;
}
