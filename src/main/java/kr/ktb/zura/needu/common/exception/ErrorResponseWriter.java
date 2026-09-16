package kr.ktb.zura.needu.common.exception;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import kr.ktb.zura.needu.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

// Spring Security, 요청 횟수 제한 등 필터 단계에서 발생한 오류의 경우에도, 동일한 응답 형식을 직접 작성할 때 사용
@Component
@RequiredArgsConstructor
public class ErrorResponseWriter {

    private final JsonMapper jsonMapper;

    public void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        write(response, errorCode, null);
    }

    public void write(HttpServletResponse response, ErrorCode errorCode, Object data) throws IOException {
        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        jsonMapper.writeValue(response.getWriter(), ApiResponse.of(errorCode.getMessage(), data));
    }
}
