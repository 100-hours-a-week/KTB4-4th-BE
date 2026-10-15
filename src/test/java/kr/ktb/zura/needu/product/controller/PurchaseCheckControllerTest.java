package kr.ktb.zura.needu.product.controller;

import java.time.LocalDateTime;
import java.util.List;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.product.dto.response.PurchaseCheckResponse;
import kr.ktb.zura.needu.product.exception.ProductErrorCode;
import kr.ktb.zura.needu.product.service.PurchaseCheckService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PurchaseCheckController.class)
class PurchaseCheckControllerTest {

    private static final Long LOGIN_USER_ID = 1L;
    private static final Long PURCHASE_CHECK_ID = 12L;
    private static final String URL = "/api/v1/purchase-checks/{purchaseCheckId}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PurchaseCheckService purchaseCheckService;

    @Test
    void validRequest_answerPurchaseCheck_returnsAnswer() throws Exception {
        given(purchaseCheckService.answerPurchaseCheck(LOGIN_USER_ID, PURCHASE_CHECK_ID, true))
                .willReturn(new PurchaseCheckResponse(
                        PURCHASE_CHECK_ID, true, LocalDateTime.of(2026, 10, 6, 8, 30)));

        mockMvc.perform(post(URL, PURCHASE_CHECK_ID).with(authenticatedUser()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purchased\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("구매 여부를 저장했습니다."))
                .andExpect(jsonPath("$.data.purchaseCheckId").value(PURCHASE_CHECK_ID))
                .andExpect(jsonPath("$.data.purchased").value(true))
                .andExpect(jsonPath("$.data.answeredAt").value("2026-10-06T08:30:00"));
    }

    @Test
    void missingPurchased_answerPurchaseCheck_returnsUnprocessableContent() throws Exception {
        mockMvc.perform(post(URL, PURCHASE_CHECK_ID).with(authenticatedUser()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableContent());

        verify(purchaseCheckService, never()).answerPurchaseCheck(anyLong(), anyLong(), anyBoolean());
    }

    @Test
    void nonBooleanPurchased_answerPurchaseCheck_returnsBadRequest() throws Exception {
        mockMvc.perform(post(URL, PURCHASE_CHECK_ID).with(authenticatedUser()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purchased\": \"maybe\"}"))
                .andExpect(status().isBadRequest());

        verify(purchaseCheckService, never()).answerPurchaseCheck(anyLong(), anyLong(), anyBoolean());
    }

    @Test
    void alreadyAnswered_answerPurchaseCheck_returnsConflict() throws Exception {
        given(purchaseCheckService.answerPurchaseCheck(LOGIN_USER_ID, PURCHASE_CHECK_ID, false))
                .willThrow(new BusinessException(ProductErrorCode.PURCHASE_CHECK_ALREADY_ANSWERED));

        mockMvc.perform(post(URL, PURCHASE_CHECK_ID).with(authenticatedUser()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purchased\": false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("이미 구매 여부를 답했습니다."))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    private static RequestPostProcessor authenticatedUser() {
        return authentication(new UsernamePasswordAuthenticationToken(LOGIN_USER_ID, null, List.of()));
    }
}
