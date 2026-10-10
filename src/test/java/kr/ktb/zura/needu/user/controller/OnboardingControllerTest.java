package kr.ktb.zura.needu.user.controller;

import java.util.List;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.user.dto.response.OnboardingStatusResponse;
import kr.ktb.zura.needu.user.exception.UserErrorCode;
import kr.ktb.zura.needu.user.service.OnboardingService;
import kr.ktb.zura.needu.user.type.OnboardingStep;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OnboardingController.class)
class OnboardingControllerTest {

    private static final Long LOGIN_USER_ID = 1L;
    private static final String URL = "/api/v1/users/me/onboarding";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OnboardingService onboardingService;

    @Test
    void onboardingInProgress_findOnboardingStatus_returnsCurrentStep() throws Exception {
        given(onboardingService.findOnboardingStatus(LOGIN_USER_ID))
                .willReturn(OnboardingStatusResponse.inProgressStatus(OnboardingStep.CONSENTS));

        mockMvc.perform(get(URL).with(authenticatedUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("온보딩 진행 상태를 조회했습니다."))
                .andExpect(jsonPath("$.data.completed").value(false))
                .andExpect(jsonPath("$.data.currentStep").value("CONSENTS"));
    }

    @Test
    void onboardingCompleted_findOnboardingStatus_returnsNullStep() throws Exception {
        given(onboardingService.findOnboardingStatus(LOGIN_USER_ID))
                .willReturn(OnboardingStatusResponse.completedStatus());

        mockMvc.perform(get(URL).with(authenticatedUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completed").value(true))
                .andExpect(jsonPath("$.data.currentStep").isEmpty());
    }

    @Test
    void blockedUser_findOnboardingStatus_returnsForbidden() throws Exception {
        given(onboardingService.findOnboardingStatus(LOGIN_USER_ID))
                .willThrow(new BusinessException(UserErrorCode.USER_BLOCKED));

        mockMvc.perform(get(URL).with(authenticatedUser()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(UserErrorCode.USER_BLOCKED.getMessage()))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    private static RequestPostProcessor authenticatedUser() {
        return authentication(new UsernamePasswordAuthenticationToken(LOGIN_USER_ID, null, List.of()));
    }
}
