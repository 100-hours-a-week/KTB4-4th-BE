package kr.ktb.zura.needu.common.openapi;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
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
    void calendarMonthlySummary_documentsParametersResponsesAndRateLimits() throws Exception {
        String operation = "$.paths['/api/v1/calendar/events'].get";
        requestSpec()
                .andExpect(jsonPath(operation + ".operationId").exists())
                .andExpect(jsonPath(operation + ".responses['200']").exists())
                .andExpect(jsonPath(operation + ".responses['400']").exists())
                .andExpect(jsonPath(operation + ".responses['401']").exists())
                .andExpect(jsonPath(operation + ".responses['403'].content['application/json'].examples.USER_BLOCKED").exists())
                .andExpect(jsonPath(operation + ".responses['403'].content['application/json'].examples.USER_ONBOARDING_REQUIRED").exists())
                .andExpect(jsonPath(operation + ".responses['429'].content['application/json'].examples.COMMON_TOO_MANY_REQUESTS.value.data.retryAfterSeconds").value(60))
                .andExpect(jsonPath(operation + ".responses['500']").exists())
                .andExpect(jsonPath(operation + ".responses['503']").exists())
                .andExpect(jsonPath(operation + ".parameters[?(@.name == 'targetMonth')].required").value(contains(true)))
                .andExpect(jsonPath(operation + ".parameters[?(@.name == 'targetMonth')].schema.type").value(contains("string")));
    }

    @Test
    void calendarDailyEvents_documentsParametersResponsesAndRateLimits() throws Exception {
        String operation = "$.paths['/api/v1/calendar/events/daily'].get";
        requestSpec()
                .andExpect(jsonPath(operation + ".operationId").exists())
                .andExpect(jsonPath(operation + ".responses['200']").exists())
                .andExpect(jsonPath(operation + ".responses['400']").exists())
                .andExpect(jsonPath(operation + ".responses['401']").exists())
                .andExpect(jsonPath(operation + ".responses['403'].content['application/json'].examples.USER_BLOCKED").exists())
                .andExpect(jsonPath(operation + ".responses['403'].content['application/json'].examples.USER_ONBOARDING_REQUIRED").exists())
                .andExpect(jsonPath(operation + ".responses['429'].content['application/json'].examples.COMMON_TOO_MANY_REQUESTS.value.data.retryAfterSeconds").value(60))
                .andExpect(jsonPath(operation + ".responses['500']").exists())
                .andExpect(jsonPath(operation + ".responses['503']").exists())
                .andExpect(jsonPath(operation + ".parameters[?(@.name == 'date')].required").value(contains(true)));
    }

    @Test
    void calendarEventDetail_documentsParametersResponsesAndRateLimits() throws Exception {
        String operation = "$.paths['/api/v1/calendar/events/{eventId}'].get";
        requestSpec()
                .andExpect(jsonPath(operation + ".operationId").exists())
                .andExpect(jsonPath(operation + ".responses['200']").exists())
                .andExpect(jsonPath(operation + ".responses['400']").exists())
                .andExpect(jsonPath(operation + ".responses['401']").exists())
                .andExpect(jsonPath(operation + ".responses['403'].content['application/json'].examples.USER_BLOCKED").exists())
                .andExpect(jsonPath(operation + ".responses['403'].content['application/json'].examples.USER_ONBOARDING_REQUIRED").exists())
                .andExpect(jsonPath(operation + ".responses['429'].content['application/json'].examples.COMMON_TOO_MANY_REQUESTS.value.data.retryAfterSeconds").value(60))
                .andExpect(jsonPath(operation + ".responses['500']").exists())
                .andExpect(jsonPath(operation + ".responses['503']").exists())
                .andExpect(jsonPath(operation + ".responses['422']").exists())
                .andExpect(jsonPath(operation + ".responses['404'].content['application/json'].examples.CALENDAR_EVENT_NOT_FOUND").exists())
                .andExpect(jsonPath("$.components.schemas.CalendarEventDetailResponse.properties.eventId.type").value("integer"))
                .andExpect(jsonPath("$.components.schemas.CalendarEventDetailResponse.properties.isAllDay.type").value("boolean"))
                .andExpect(jsonPath("$.components.schemas.CalendarEventDetailResponse.properties.isNotificationEnabled.type").value("boolean"))
                .andExpect(jsonPath("$.components.schemas.CalendarEventDetailResponse.properties.startAt.format").value("date-time"));
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
                .andExpect(jsonPath("$.paths['/api/v1/notifications/web-push/public-key'].get.responses['401']")
                        .doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/notifications/web-push/public-key'].get.responses['503']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/friends/{userId}'].get.responses['401']").exists());
    }

    @Test
    void parameterObject_expandsToQueryParameters() throws Exception {
        requestSpec()
                .andExpect(jsonPath("$.paths['/api/v1/friends'].get.parameters[*].name")
                        .value(containsInAnyOrder("favorite", "keyword", "cursor", "size")))
                .andExpect(jsonPath("$.paths['/api/v1/friends'].get.parameters[?(@.name == 'size')].required")
                        .value(contains(true)));
    }

    @Test
    void requestObjectWithoutAnnotation_expandsToQueryParametersExceptPrincipal() throws Exception {
        requestSpec()
                .andExpect(jsonPath("$.paths['/api/v1/users/me/personal-recommendations'].get.parameters[*].name")
                        .value(containsInAnyOrder("minPrice", "maxPrice", "category", "cursor", "size")))
                .andExpect(jsonPath("$.paths['/api/v1/notifications'].get.parameters[*].name")
                        .value(containsInAnyOrder("category", "cursor", "size", "since")));
    }

    @Test
    void requestObjectWithNonStringField_hasInvalidRequestResponse() throws Exception {
        requestSpec()
                .andExpect(jsonPath("$.paths['/api/v1/users/me/personal-recommendations'].get.responses['400']"
                        + ".content['application/json'].examples.COMMON_INVALID_REQUEST").exists())
                .andExpect(jsonPath("$.paths['/api/v1/notifications'].get.responses['400']"
                        + ".content['application/json'].examples.COMMON_INVALID_REQUEST").exists());
    }

    private ResultActions requestSpec() throws Exception {
        return mockMvc.perform(get("/v3/api-docs"));
    }
}
