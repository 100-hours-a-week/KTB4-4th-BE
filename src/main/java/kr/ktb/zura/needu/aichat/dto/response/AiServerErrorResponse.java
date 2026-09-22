package kr.ktb.zura.needu.aichat.dto.response;

import java.util.Map;

// AI 명세의 공통 오류 응답. 같은 상태 코드에 여러 원인이 있을 때 code로 구분한다.
public record AiServerErrorResponse(
        String code,
        String message,
        Boolean retryable,
        String requestId,
        Map<String, Object> details
) {
}
