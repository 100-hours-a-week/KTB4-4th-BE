package kr.ktb.zura.needu.product.controller;

import java.util.List;

import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.CommonErrorCode;
import kr.ktb.zura.needu.common.response.CursorPageResponse;
import kr.ktb.zura.needu.friend.exception.FriendErrorCode;
import kr.ktb.zura.needu.product.dto.request.GiftProductSearchCondition;
import kr.ktb.zura.needu.product.dto.response.GiftProductResponse;
import kr.ktb.zura.needu.product.exception.ProductErrorCode;
import kr.ktb.zura.needu.product.service.GiftProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GiftProductController.class)
class GiftProductControllerTest {

    private static final Long LOGIN_USER_ID = 1L;
    private static final Long FRIEND_USER_ID = 123L;
    private static final String URL = "/api/v1/users/{userId}/gift-recommendations";
    private static final GiftProductSearchCondition FIRST_PAGE_CONDITION =
            new GiftProductSearchCondition(30000L, 50000L, null, 20);
    private static final GiftProductResponse CROSS_BAG = new GiftProductResponse(
            101L, 1001L, "https://example.com/products/1001.jpg", "FASHION", "미니 크로스백", 49000L,
            List.of("미니멀", "데일리"), "데일리룩을 즐겨 입어요.");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GiftProductService giftProductService;

    @Test
    void giftProductsExist_findAllGiftProducts_returnsItemsWithTopLevelCursor() throws Exception {
        given(giftProductService.findAllGiftProducts(LOGIN_USER_ID, FRIEND_USER_ID, FIRST_PAGE_CONDITION))
                .willReturn(new CursorPageResponse<>(List.of(CROSS_BAG), "next-cursor", true));

        mockMvc.perform(firstPageRequest())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("추천 상품을 조회했습니다."))
                .andExpect(jsonPath("$.data.items[0].recommendationId").value(101))
                .andExpect(jsonPath("$.data.items[0].productId").value(1001))
                .andExpect(jsonPath("$.data.items[0].productImageUrl")
                        .value("https://example.com/products/1001.jpg"))
                .andExpect(jsonPath("$.data.items[0].category").value("FASHION"))
                .andExpect(jsonPath("$.data.items[0].name").value("미니 크로스백"))
                .andExpect(jsonPath("$.data.items[0].price").value(49000))
                .andExpect(jsonPath("$.data.items[0].matchingKeywords[0]").value("미니멀"))
                .andExpect(jsonPath("$.data.items[0].matchingKeywords[1]").value("데일리"))
                .andExpect(jsonPath("$.data.items[0].reason").value("데일리룩을 즐겨 입어요."))
                .andExpect(jsonPath("$.nextCursor").value("next-cursor"))
                .andExpect(jsonPath("$.hasNext").value(true));
    }

    @Test
    void noGiftProducts_findAllGiftProducts_returnsEmptyItems() throws Exception {
        given(giftProductService.findAllGiftProducts(LOGIN_USER_ID, FRIEND_USER_ID, FIRST_PAGE_CONDITION))
                .willReturn(new CursorPageResponse<>(List.of(), null, false));

        mockMvc.perform(firstPageRequest())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.nextCursor").isEmpty())
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void cursorGiven_findAllGiftProducts_passesConditionToService() throws Exception {
        GiftProductSearchCondition condition = new GiftProductSearchCondition(30000L, 50000L, "cursor", 10);
        given(giftProductService.findAllGiftProducts(LOGIN_USER_ID, FRIEND_USER_ID, condition))
                .willReturn(new CursorPageResponse<>(List.of(), null, false));

        mockMvc.perform(get(URL, FRIEND_USER_ID)
                        .param("minPrice", "30000")
                        .param("maxPrice", "50000")
                        .param("cursor", "cursor")
                        .param("size", "10")
                        .with(authenticatedUser()))
                .andExpect(status().isOk());

        verify(giftProductService).findAllGiftProducts(LOGIN_USER_ID, FRIEND_USER_ID, condition);
    }

    @Test
    void requiredParameterMissing_findAllGiftProducts_returnsBadRequest() throws Exception {
        mockMvc.perform(get(URL, FRIEND_USER_ID)
                        .param("minPrice", "30000")
                        .param("size", "20")
                        .with(authenticatedUser()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("요청 형식이 올바르지 않습니다."))
                .andExpect(jsonPath("$.data").isEmpty());

        mockMvc.perform(get(URL, FRIEND_USER_ID)
                        .param("minPrice", "30000")
                        .param("maxPrice", "50000")
                        .with(authenticatedUser()))
                .andExpect(status().isBadRequest());

        verify(giftProductService, never()).findAllGiftProducts(anyLong(), anyLong(), any());
    }

    @Test
    void nonNumericParameter_findAllGiftProducts_returnsBadRequest() throws Exception {
        mockMvc.perform(get(URL, "abc")
                        .param("minPrice", "30000")
                        .param("maxPrice", "50000")
                        .param("size", "20")
                        .with(authenticatedUser()))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get(URL, FRIEND_USER_ID)
                        .param("minPrice", "abc")
                        .param("maxPrice", "50000")
                        .param("size", "20")
                        .with(authenticatedUser()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("요청 형식이 올바르지 않습니다."));
    }

    @Test
    void invalidInput_findAllGiftProducts_returnsUnprocessableContent() throws Exception {
        mockMvc.perform(get(URL, FRIEND_USER_ID)
                        .param("minPrice", "-1")
                        .param("maxPrice", "50000")
                        .param("size", "20")
                        .with(authenticatedUser()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("입력값이 유효하지 않습니다. 입력 내용을 확인해 주세요."))
                .andExpect(jsonPath("$.data").isEmpty());

        mockMvc.perform(get(URL, FRIEND_USER_ID)
                        .param("minPrice", "30000")
                        .param("maxPrice", "50000")
                        .param("size", "51")
                        .with(authenticatedUser()))
                .andExpect(status().isUnprocessableContent());

        verify(giftProductService, never()).findAllGiftProducts(anyLong(), anyLong(), any());
    }

    @Test
    void friendNotFound_findAllGiftProducts_returnsNotFound() throws Exception {
        given(giftProductService.findAllGiftProducts(LOGIN_USER_ID, FRIEND_USER_ID, FIRST_PAGE_CONDITION))
                .willThrow(new BusinessException(FriendErrorCode.FRIEND_NOT_FOUND));

        mockMvc.perform(firstPageRequest())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("친구를 찾을 수 없습니다."))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void recommendationForbidden_findAllGiftProducts_returnsForbidden() throws Exception {
        given(giftProductService.findAllGiftProducts(LOGIN_USER_ID, FRIEND_USER_ID, FIRST_PAGE_CONDITION))
                .willThrow(new BusinessException(ProductErrorCode.PRODUCT_GIFT_RECOMMENDATION_FORBIDDEN));

        mockMvc.perform(firstPageRequest())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("해당 친구의 추천 상품을 조회할 수 없습니다."))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void invalidCursor_findAllGiftProducts_returnsBadRequest() throws Exception {
        given(giftProductService.findAllGiftProducts(LOGIN_USER_ID, FRIEND_USER_ID, FIRST_PAGE_CONDITION))
                .willThrow(new BusinessException(CommonErrorCode.COMMON_INVALID_REQUEST));

        mockMvc.perform(firstPageRequest())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("요청 형식이 올바르지 않습니다."));
    }

    @Test
    void unexpectedException_findAllGiftProducts_returnsInternalServerError() throws Exception {
        given(giftProductService.findAllGiftProducts(LOGIN_USER_ID, FRIEND_USER_ID, FIRST_PAGE_CONDITION))
                .willThrow(new IllegalStateException());

        mockMvc.perform(firstPageRequest())
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("요청을 처리하지 못했습니다."))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    private MockHttpServletRequestBuilder firstPageRequest() {
        return get(URL, FRIEND_USER_ID)
                .param("minPrice", "30000")
                .param("maxPrice", "50000")
                .param("size", "20")
                .with(authenticatedUser());
    }

    private static RequestPostProcessor authenticatedUser() {
        return authentication(new UsernamePasswordAuthenticationToken(LOGIN_USER_ID, null, List.of()));
    }
}
