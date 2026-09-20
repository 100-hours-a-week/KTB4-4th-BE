package kr.ktb.zura.needu.aichat.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record SendMessageRequest(
        @NotNull UUID clientMessageId,
        @NotBlank @Size(min = 1, max = 500) String content
) {
}
