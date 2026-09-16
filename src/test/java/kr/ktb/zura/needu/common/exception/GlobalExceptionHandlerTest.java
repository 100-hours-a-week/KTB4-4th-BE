package kr.ktb.zura.needu.common.exception;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ExceptionTestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void businessException_returnsErrorCodeStatusAndMessage() throws Exception {
        mockMvc.perform(get("/test/business"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("일시적으로 서비스를 이용할 수 없습니다."))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void tooManyRequestsException_returnsRetryAfterSeconds() throws Exception {
        mockMvc.perform(get("/test/rate-limit"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."))
                .andExpect(jsonPath("$.data.retryAfterSeconds").value(10));
    }

    @Test
    void malformedJsonBody_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/test/body").contentType(MediaType.APPLICATION_JSON).content("{invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("요청 형식이 올바르지 않습니다."))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void typeMismatchedParameter_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/test/param").param("size", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("요청 형식이 올바르지 않습니다."));
    }

    @Test
    void missingRequiredHeader_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/test/header"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("요청 형식이 올바르지 않습니다."));
    }

    @Test
    void invalidRequestBody_returnsUnprocessableContent() throws Exception {
        mockMvc.perform(post("/test/body").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("입력값이 유효하지 않습니다. 입력 내용을 확인해 주세요."))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void outOfRangeParameter_returnsUnprocessableContent() throws Exception {
        mockMvc.perform(get("/test/param").param("size", "101"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("입력값이 유효하지 않습니다. 입력 내용을 확인해 주세요."));
    }

    @Test
    void unsupportedMethod_returnsMethodNotAllowed() throws Exception {
        mockMvc.perform(delete("/test/business"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.message").value("지원하지 않는 요청 방식입니다."));
    }

    @Test
    void unexpectedException_returnsInternalServerError() throws Exception {
        mockMvc.perform(get("/test/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("요청을 처리하지 못했습니다."))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void accessDeniedException_propagatesToSecurityFilter() {
        assertThatThrownBy(() -> mockMvc.perform(get("/test/access-denied")))
                .hasRootCauseInstanceOf(AccessDeniedException.class);
    }

    @RestController
    static class ExceptionTestController {

        @GetMapping("/test/business")
        void throwBusinessException() {
            throw new BusinessException(CommonErrorCode.COMMON_SERVICE_UNAVAILABLE);
        }

        @GetMapping("/test/rate-limit")
        void throwTooManyRequestsException() {
            throw new TooManyRequestsException(10);
        }

        @PostMapping("/test/body")
        void receiveBody(@Valid @RequestBody TestRequest request) {
        }

        @GetMapping("/test/param")
        void receiveParam(@RequestParam @Max(100) Integer size) {
        }

        @GetMapping("/test/header")
        void receiveHeader(@RequestHeader("Idempotency-Key") String idempotencyKey) {
        }

        @GetMapping("/test/unexpected")
        void throwUnexpectedException() {
            throw new IllegalStateException();
        }

        @GetMapping("/test/access-denied")
        void throwAccessDeniedException() {
            throw new AccessDeniedException("denied");
        }
    }

    record TestRequest(@NotBlank String name) {
    }
}
