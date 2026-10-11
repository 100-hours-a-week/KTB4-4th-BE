package kr.ktb.zura.needu.product.controller;

import java.util.List;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.product.dto.response.ProductCategoryResponse;
import kr.ktb.zura.needu.product.dto.response.ProductDetailResponse;
import kr.ktb.zura.needu.product.dto.response.ProductFeedbackResponse;
import kr.ktb.zura.needu.product.dto.response.ProductRecommendationResponse;
import kr.ktb.zura.needu.product.exception.ProductErrorCode;
import kr.ktb.zura.needu.product.service.ProductFeedbackService;
import kr.ktb.zura.needu.product.service.ProductService;
import kr.ktb.zura.needu.product.type.ProductCategory;
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
    private static final Long FRIEND_USER_ID = 123L;
    private static final String CATEGORIES_URL = "/api/v1/products/categories";
    private static final String DETAIL_URL = "/api/v1/products/{productId}";
    private static final String FEEDBACK_URL = "/api/v1/products/{productId}/feedback";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    @MockitoBean
    private ProductFeedbackService productFeedbackService;

    @Test
    void loggedInUser_findAllCategories_returnsCodeAndNameItems() throws Exception {
        given(productService.findAllCategories()).willReturn(List.of(
                new ProductCategoryResponse("VOUCHER", "교환권"),
                new ProductCategoryResponse("LIVING", "리빙")));

        mockMvc.perform(get(CATEGORIES_URL).with(authenticatedUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("카테고리 목록을 조회했습니다."))
                .andExpect(jsonPath("$.data.items[0].code").value("VOUCHER"))
                .andExpect(jsonPath("$.data.items[0].name").value("교환권"))
                .andExpect(jsonPath("$.data.items[1].code").value("LIVING"));
    }

    @Test
    void friendGiftContext_findProduct_returnsDetailWithRecommendation() throws Exception {
        given(productService.findProduct(LOGIN_USER_ID, PRODUCT_ID, ProductContext.FRIEND_GIFT, FRIEND_USER_ID))
                .willReturn(new ProductDetailResponse(
                        PRODUCT_ID, "섬유유연제", ProductCategory.LIVING, "상품 설명", 16500L,
                        "https://image.test/product.png", "https://shop.test/product",
                        new ProductRecommendationResponse(55L, "향기 취향과 잘 맞아요.", List.of("향기", "홈케어")),
                        null));

        mockMvc.perform(get(DETAIL_URL, PRODUCT_ID)
                        .param("context", "FRIEND_GIFT")
                        .param("userId", String.valueOf(FRIEND_USER_ID))
                        .with(authenticatedUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("상품 정보를 조회했습니다."))
                .andExpect(jsonPath("$.data.productId").value(PRODUCT_ID))
                .andExpect(jsonPath("$.data.category").value("LIVING"))
                .andExpect(jsonPath("$.data.price").value(16500))
                .andExpect(jsonPath("$.data.recommendation.recommendationId").value(55))
                .andExpect(jsonPath("$.data.recommendation.matchingKeywords[1]").value("홈케어"))
                .andExpect(jsonPath("$.data.myFeedback").isEmpty());
    }

    @Test
    void personalContextWithoutUserId_findProduct_passesNullFriendUserId() throws Exception {
        given(productService.findProduct(LOGIN_USER_ID, PRODUCT_ID, ProductContext.PERSONAL, null))
                .willReturn(new ProductDetailResponse(
                        PRODUCT_ID, "섬유유연제", ProductCategory.LIVING, null, 16500L, null, null,
                        new ProductRecommendationResponse(55L, null, List.of()), ProductFeedbackType.LIKE));

        mockMvc.perform(get(DETAIL_URL, PRODUCT_ID).param("context", "PERSONAL").with(authenticatedUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recommendation.matchingKeywords").isEmpty())
                .andExpect(jsonPath("$.data.myFeedback").value("LIKE"));
    }

    @Test
    void recommendationNotFound_findProduct_returnsNotFound() throws Exception {
        given(productService.findProduct(LOGIN_USER_ID, PRODUCT_ID, ProductContext.MY_GIFT, null))
                .willThrow(new BusinessException(ProductErrorCode.PRODUCT_RECOMMENDATION_NOT_FOUND));

        mockMvc.perform(get(DETAIL_URL, PRODUCT_ID).param("context", "MY_GIFT").with(authenticatedUser()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("추천 상품을 찾을 수 없습니다."))
                .andExpect(jsonPath("$.data").isEmpty());
    }

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
    void nullFeedback_updateFeedback_returnsUnprocessableContent() throws Exception {
        mockMvc.perform(put(FEEDBACK_URL, PRODUCT_ID).with(authenticatedUser()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"context\": \"MY_GIFT\", \"feedback\": null}"))
                .andExpect(status().isUnprocessableContent());

        verify(productFeedbackService, never()).updateFeedback(anyLong(), anyLong(), any(), any());
    }

    @Test
    void missingFeedback_updateFeedback_returnsUnprocessableContent() throws Exception {
        mockMvc.perform(put(FEEDBACK_URL, PRODUCT_ID).with(authenticatedUser()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"context\": \"PERSONAL\"}"))
                .andExpect(status().isUnprocessableContent());

        verify(productFeedbackService, never()).updateFeedback(anyLong(), anyLong(), any(), any());
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
