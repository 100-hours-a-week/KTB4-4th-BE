package kr.ktb.zura.needu.user.controller;

import java.util.List;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.CommonErrorCode;
import kr.ktb.zura.needu.user.dto.request.UpdateGiftPreferenceRequest;
import kr.ktb.zura.needu.user.dto.response.GiftPreferenceResponse;
import kr.ktb.zura.needu.user.dto.response.GiftPreferenceResultResponse;
import kr.ktb.zura.needu.user.exception.UserErrorCode;
import kr.ktb.zura.needu.user.service.GiftPreferenceService;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GiftPreferenceController.class)
class GiftPreferenceControllerTest {

    private static final Long LOGIN_USER_ID = 1L;
    private static final String URL = "/api/v1/users/me/gift-preferences";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GiftPreferenceService giftPreferenceService;

    @Test
    void savedPreference_findGiftPreference_returnsCodes() throws Exception {
        given(giftPreferenceService.findGiftPreference(LOGIN_USER_ID)).willReturn(new GiftPreferenceResponse(
                true, List.of("BEAUTY", "HOME_INTERIOR"), List.of("NUTS"), List.of("PERFUME")));

        mockMvc.perform(get(URL).with(authenticatedUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("저장된 취향 정보를 조회했습니다."))
                .andExpect(jsonPath("$.data.exists").value(true))
                .andExpect(jsonPath("$.data.interestCategoryCodes[1]").value("HOME_INTERIOR"))
                .andExpect(jsonPath("$.data.allergyCodes[0]").value("NUTS"))
                .andExpect(jsonPath("$.data.giftExclusionCodes[0]").value("PERFUME"))
                .andExpect(jsonPath("$.data.updatedAt").doesNotExist());
    }

    @Test
    void noSavedPreference_findGiftPreference_returnsEmptyArrays() throws Exception {
        given(giftPreferenceService.findGiftPreference(LOGIN_USER_ID)).willReturn(GiftPreferenceResponse.empty());

        mockMvc.perform(get(URL).with(authenticatedUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.exists").value(false))
                .andExpect(jsonPath("$.data.interestCategoryCodes").isEmpty());
    }

    @Test
    void onboardingRequiredUser_findGiftPreference_returnsForbidden() throws Exception {
        given(giftPreferenceService.findGiftPreference(LOGIN_USER_ID))
                .willThrow(new BusinessException(UserErrorCode.USER_ONBOARDING_REQUIRED));

        mockMvc.perform(get(URL).with(authenticatedUser()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(UserErrorCode.USER_ONBOARDING_REQUIRED.getMessage()));
    }

    @Test
    void validRequest_updateGiftPreference_returnsUpdatedPreference() throws Exception {
        UpdateGiftPreferenceRequest request = new UpdateGiftPreferenceRequest(
                List.of("FASHION"), List.of(), List.of("ALCOHOL"));
        given(giftPreferenceService.updateGiftPreference(LOGIN_USER_ID, request)).willReturn(
                new GiftPreferenceResultResponse(List.of("FASHION"), List.of(), List.of("ALCOHOL")));

        mockMvc.perform(updateGiftPreference("""
                        {"interestCategoryCodes": ["FASHION"], "allergyCodes": [], "giftExclusionCodes": ["ALCOHOL"]}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("취향 정보를 수정했습니다."))
                .andExpect(jsonPath("$.data.interestCategoryCodes[0]").value("FASHION"))
                .andExpect(jsonPath("$.data.allergyCodes").isEmpty())
                .andExpect(jsonPath("$.data.updatedAt").doesNotExist());
    }

    @Test
    void missingField_updateGiftPreference_returnsUnprocessableContent() throws Exception {
        mockMvc.perform(updateGiftPreference("""
                        {"interestCategoryCodes": ["FASHION"], "allergyCodes": []}
                        """))
                .andExpect(status().isUnprocessableContent());

        verify(giftPreferenceService, never()).updateGiftPreference(any(), any());
    }

    @Test
    void moreThanFiveInterestCategories_updateGiftPreference_returnsUnprocessableContent() throws Exception {
        mockMvc.perform(updateGiftPreference("""
                        {"interestCategoryCodes": ["FASHION", "BEAUTY", "TRAVEL", "GAME", "MUSIC", "PET"],
                         "allergyCodes": [], "giftExclusionCodes": []}
                        """))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void unknownCode_updateGiftPreference_returnsUnprocessableContent() throws Exception {
        given(giftPreferenceService.updateGiftPreference(eq(LOGIN_USER_ID), any()))
                .willThrow(new BusinessException(CommonErrorCode.COMMON_INVALID_INPUT));

        mockMvc.perform(updateGiftPreference("""
                        {"interestCategoryCodes": ["HOME_INTERIOR"], "allergyCodes": [], "giftExclusionCodes": []}
                        """))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value(CommonErrorCode.COMMON_INVALID_INPUT.getMessage()));
    }

    @Test
    void notArray_updateGiftPreference_returnsBadRequest() throws Exception {
        mockMvc.perform(updateGiftPreference("""
                        {"interestCategoryCodes": "FASHION", "allergyCodes": [], "giftExclusionCodes": []}
                        """))
                .andExpect(status().isBadRequest());
    }

    private static MockHttpServletRequestBuilder updateGiftPreference(String body) {
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
