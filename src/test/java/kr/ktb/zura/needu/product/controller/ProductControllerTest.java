package kr.ktb.zura.needu.product.controller;

import java.util.List;
import kr.ktb.zura.needu.product.dto.response.ProductFeedbackResponse;
import kr.ktb.zura.needu.product.service.ProductFeedbackService;
import kr.ktb.zura.needu.product.service.ProductService;
import kr.ktb.zura.needu.product.type.ProductContext;
import kr.ktb.zura.needu.product.type.ProductFeedbackType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
class ProductControllerTest {

    private static final Long LOGIN_USER_ID = 1L;
    private static final Long PRODUCT_ID = 2992L;
    private static final String DETAIL_URL = "/api/v1/products/{productId}";
    private static final String FEEDBACK_URL = "/api/v1/products/{productId}/feedback";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    @MockitoBean
    private ProductFeedbackService productFeedbackService;

    @Test
    void contextMissing_findProduct_returnsUnprocessableContent() throws Exception {
        mockMvc.perform(get(DETAIL_URL, PRODUCT_ID).with(authenticatedUser()))
                .andExpect(status().isUnprocessableContent());

        verify(productService, never()).findProduct(anyLong(), anyLong(), any(), any());
    }

    @Test
    void unknownContext_findProduct_returnsUnprocessableContent() throws Exception {
        mockMvc.perform(get(DETAIL_URL, PRODUCT_ID).param("context", "OTHER").with(authenticatedUser()))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void nonNumericUserId_findProduct_returnsBadRequest() throws Exception {
        mockMvc.perform(get(DETAIL_URL, PRODUCT_ID)
                        .param("context", "FRIEND_GIFT")
                        .param("userId", "abc")
                        .with(authenticatedUser()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void likeGiven_updateFeedback_returnsSavedFeedback() throws Exception {
        given(productFeedbackService.updateFeedback(
                LOGIN_USER_ID, PRODUCT_ID, ProductContext.PERSONAL, ProductFeedbackType.LIKE))
                .willReturn(new ProductFeedbackResponse(PRODUCT_ID, ProductContext.PERSONAL, ProductFeedbackType.LIKE));

        mockMvc.perform(put(FEEDBACK_URL, PRODUCT_ID).with(authenticatedUser()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"context\": \"PERSONAL\", \"feedback\": \"LIKE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("만족도를 저장했습니다."))
                .andExpect(jsonPath("$.data.context").value("PERSONAL"))
                .andExpect(jsonPath("$.data.feedback").value("LIKE"));
    }

    @Test
    void nullFeedback_updateFeedback_passesCancellation() throws Exception {
        given(productFeedbackService.updateFeedback(LOGIN_USER_ID, PRODUCT_ID, ProductContext.MY_GIFT, null))
                .willReturn(new ProductFeedbackResponse(PRODUCT_ID, ProductContext.MY_GIFT, null));

        mockMvc.perform(put(FEEDBACK_URL, PRODUCT_ID).with(authenticatedUser()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"context\": \"MY_GIFT\", \"feedback\": null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.feedback").isEmpty());
    }

    @Test
    void friendGiftContext_updateFeedback_returnsUnprocessableContent() throws Exception {
        mockMvc.perform(put(FEEDBACK_URL, PRODUCT_ID).with(authenticatedUser()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"context\": \"FRIEND_GIFT\", \"feedback\": \"LIKE\"}"))
                .andExpect(status().isUnprocessableContent());

        verify(productFeedbackService, never()).updateFeedback(anyLong(), anyLong(), any(), any());
    }

    private static RequestPostProcessor authenticatedUser() {
        return authentication(new UsernamePasswordAuthenticationToken(LOGIN_USER_ID, null, List.of()));
    }
}
