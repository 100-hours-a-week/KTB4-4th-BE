package kr.ktb.zura.needu.feedback.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public record ErrorContextRequest(
        @NotBlank @Size(max = MAX_ERROR_TYPE_LENGTH) String errorType,
        @NotBlank @Size(max = MAX_ERROR_CODE_LENGTH) String errorCode,
        @NotNull OffsetDateTime occurredAt,
        @NotBlank @Size(max = MAX_APP_VERSION_LENGTH) String appVersion
) {

    private static final int MAX_ERROR_TYPE_LENGTH = 30;
    private static final int MAX_ERROR_CODE_LENGTH = 50;
    private static final int MAX_APP_VERSION_LENGTH = 30;
}
