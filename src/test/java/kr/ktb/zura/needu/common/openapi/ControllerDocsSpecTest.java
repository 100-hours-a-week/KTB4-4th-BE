package kr.ktb.zura.needu.common.openapi;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

// 도메인별 *ControllerDocs에 등록한 정보가 생성된 스펙에 반영되는지 확인한다.
@SpringBootTest(properties = {
        "springdoc.api-docs.enabled=true",
        "springdoc.default-produces-media-type=application/json"
})
@AutoConfigureMockMvc(addFilters = false)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class ControllerDocsSpecTest {

    private final MockMvc mockMvc;

    ControllerDocsSpecTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void successStatus_replacesDefault200() throws Exception {
        requestSpec()
                .andExpect(jsonPath("$.paths['/api/v1/error-reports'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/error-reports'].post.responses['200']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/auth/kakao/authorize'].get.responses['302'].headers.Location")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/logout'].post.responses['204']").exists());
    }

    @Test
    void domainErrors_addedWithErrorCodeMessage() throws Exception {
        requestSpec()
                .andExpect(jsonPath("$.paths['/api/v1/friends/{userId}'].get.responses['404']"
                        + ".content['application/json'].examples.FRIEND_NOT_FOUND.value.message")
                        .value("친구를 찾을 수 없습니다."))
                .andExpect(jsonPath("$.paths['/api/v1/ai/conversations/{conversationId}/analysis'].post"
                        + ".responses['409'].content['application/json'].examples.AICHAT_PROFILE_TOO_SPARSE"
                        + ".value.data.restartRequired").value(true));
    }

    @Test
    void publicApi_hasNoUnauthorizedResponse() throws Exception {
        requestSpec()
                .andExpect(jsonPath("$.paths['/api/v1/auth/kakao/authorize'].get.responses['401']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/friends/{userId}'].get.responses['401']").exists());
    }

    private ResultActions requestSpec() throws Exception {
        return mockMvc.perform(get("/v3/api-docs"));
    }
}
