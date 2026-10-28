package kr.ktb.zura.needu.anonymouschat.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record SendAnonymousMessageRequest(
        @NotNull UUID clientMessageId,
        @NotBlank @Size(max = SendAnonymousMessageRequest.CONTENT_MAX_LENGTH) String content
) {

    public static final int CONTENT_MAX_LENGTH = 500;
}
