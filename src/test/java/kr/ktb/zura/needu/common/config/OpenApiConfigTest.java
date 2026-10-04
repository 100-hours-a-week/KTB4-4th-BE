package kr.ktb.zura.needu.common.config;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 문서 경로는 운영 Security 설정에서 열지 않으므로 필터 없이 스펙만 조회한다.
// CI(api-docs 워크플로)는 이 테스트가 남긴 파일을 Cloudflare Pages에 배포한다.
@SpringBootTest(properties = "springdoc.api-docs.enabled=true")
@AutoConfigureMockMvc(addFilters = false)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class OpenApiConfigTest {

    private static final Path SPEC_OUTPUT = Path.of("build", "openapi", "openapi.json");

    private final MockMvc mockMvc;

    OpenApiConfigTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void apiDocsRequest_returnsSpecWithCookieAuth_andWritesSpecFile() throws Exception {
        String spec = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("NeedU API"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/kakao/authorize']").exists())
                .andExpect(jsonPath("$.components.securitySchemes.accessTokenCookie.in").value("cookie"))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        Files.createDirectories(SPEC_OUTPUT.getParent());
        Files.writeString(SPEC_OUTPUT, spec, StandardCharsets.UTF_8);
    }
}
