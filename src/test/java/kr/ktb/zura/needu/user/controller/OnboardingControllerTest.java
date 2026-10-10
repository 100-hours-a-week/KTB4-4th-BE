package kr.ktb.zura.needu.user.controller;

import java.time.LocalDate;
import java.util.List;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.user.dto.request.CompleteOnboardingRequest;
import kr.ktb.zura.needu.user.dto.response.OnboardingStatusResponse;
import kr.ktb.zura.needu.user.exception.UserErrorCode;
import kr.ktb.zura.needu.user.service.OnboardingService;
import kr.ktb.zura.needu.user.type.Gender;
import kr.ktb.zura.needu.user.type.OnboardingStep;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

    @Test
    void validRequest_completeOnboarding_returnsCompleted() throws Exception {
        mockMvc.perform(completeOnboarding("""
                        {"gender": "MALE", "birthDate": "2000-01-01",
                         "interestCategoryCodes": ["FASHION", "BEAUTY"],
                         "allergyCodes": ["PEANUT"], "giftExclusionCodes": ["PERFUME"]}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("온보딩을 완료했습니다."))
                .andExpect(jsonPath("$.data").isEmpty());

        verify(onboardingService).completeOnboarding(LOGIN_USER_ID, new CompleteOnboardingRequest(
                Gender.MALE, LocalDate.of(2000, 1, 1), List.of("FASHION", "BEAUTY"), List.of("PEANUT"),
                List.of("PERFUME")));
    }

    @Test
    void genderMissing_completeOnboarding_returnsUnprocessableContent() throws Exception {
        mockMvc.perform(completeOnboarding("""
                        {"birthDate": "2000-01-01"}
                        """))
                .andExpect(status().isUnprocessableContent());

        verify(onboardingService, never()).completeOnboarding(any(), any());
    }

    @Test
    void futureBirthDate_completeOnboarding_returnsUnprocessableContent() throws Exception {
        mockMvc.perform(completeOnboarding("""
                        {"gender": "FEMALE", "birthDate": "%s"}
                        """.formatted(LocalDate.now().plusDays(1))))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void moreThanFiveInterestCategories_completeOnboarding_returnsUnprocessableContent() throws Exception {
        mockMvc.perform(completeOnboarding("""
                        {"gender": "NONE", "birthDate": "2000-01-01",
                         "interestCategoryCodes": ["FASHION", "BEAUTY", "TRAVEL", "GAME", "MUSIC", "PET"]}
                        """))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void invalidDateFormat_completeOnboarding_returnsBadRequest() throws Exception {
        mockMvc.perform(completeOnboarding("""
                        {"gender": "MALE", "birthDate": "2000/01/01"}
                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void alreadyCompleted_completeOnboarding_returnsConflict() throws Exception {
        willThrow(new BusinessException(UserErrorCode.USER_ONBOARDING_ALREADY_COMPLETED))
                .given(onboardingService).completeOnboarding(eq(LOGIN_USER_ID), any());

        mockMvc.perform(completeOnboarding("""
                        {"gender": "MALE", "birthDate": "2000-01-01"}
                        """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(UserErrorCode.USER_ONBOARDING_ALREADY_COMPLETED.getMessage()));
    }

    @Test
    void requiredConsentMissing_completeOnboarding_returnsForbidden() throws Exception {
        willThrow(new BusinessException(UserErrorCode.USER_REQUIRED_CONSENT_MISSING))
                .given(onboardingService).completeOnboarding(eq(LOGIN_USER_ID), any());

        mockMvc.perform(completeOnboarding("""
                        {"gender": "MALE", "birthDate": "2000-01-01"}
                        """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(UserErrorCode.USER_REQUIRED_CONSENT_MISSING.getMessage()));
    }

    private static MockHttpServletRequestBuilder completeOnboarding(String body) {
        return put(URL)
                .with(authenticatedUser())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private static RequestPostProcessor authenticatedUser() {
        return authentication(new UsernamePasswordAuthenticationToken(LOGIN_USER_ID, null, List.of()));
    }
}
