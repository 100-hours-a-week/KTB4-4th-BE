package kr.ktb.zura.needu.product.controller;

import java.math.BigDecimal;
import java.util.List;

import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.CommonErrorCode;
import kr.ktb.zura.needu.product.dto.request.GiftProductSearchCondition;
import kr.ktb.zura.needu.product.dto.response.MyGiftProductResponse;
import kr.ktb.zura.needu.product.dto.response.PriceRangeResponse;
import kr.ktb.zura.needu.product.dto.response.ProductCursorPageResponse;
import kr.ktb.zura.needu.product.service.GiftProductService;
import kr.ktb.zura.needu.product.type.ProductCategory;
import kr.ktb.zura.needu.product.type.ProductFeedbackType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MyGiftProductController.class)
class MyGiftProductControllerTest {

    private static final Long USER_ID = 1L;
    private static final String URL = "/api/v1/users/me/gift-recommendations";
    private static final MyGiftProductResponse CROSS_BAG = new MyGiftProductResponse(
            101L, 1001L, "미니 크로스백", "https://example.com/products/1001.jpg",
            "https://example.com/products/1001", ProductCategory.FASHION, 49000L,
            new BigDecimal("0.900000"), List.of("미니멀", "데일리"), "데일리룩을 즐겨 입어요.",
            ProductFeedbackType.LIKE);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GiftProductService giftProductService;

    @Test
    void categoryGiven_findAllMyGiftProducts_returnsItemsWithKeywordsAndMyFeedback() throws Exception {
        GiftProductSearchCondition condition = new GiftProductSearchCondition(30000L, 50000L, "FASHION", null, 20);
        given(giftProductService.findAllMyGiftProducts(USER_ID, condition))
                .willReturn(new ProductCursorPageResponse<>(
                        List.of(CROSS_BAG), new PriceRangeResponse(15000L, 230000L), "next-cursor", true));

        mockMvc.perform(get(URL)
                        .param("minPrice", "30000")
                        .param("maxPrice", "50000")
                        .param("category", "FASHION")
                        .with(authenticatedUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("선물 추천 상품 목록을 조회했습니다."))
                .andExpect(jsonPath("$.data.items[0].recommendationId").value(101))
                .andExpect(jsonPath("$.data.items[0].category").value("FASHION"))
                .andExpect(jsonPath("$.data.items[0].matchingKeywords[0]").value("미니멀"))
                .andExpect(jsonPath("$.data.items[0].reason").value("데일리룩을 즐겨 입어요."))
                .andExpect(jsonPath("$.data.items[0].myFeedback").value("LIKE"))
                .andExpect(jsonPath("$.data.priceRange.minPrice").value(15000))
                .andExpect(jsonPath("$.nextCursor").value("next-cursor"))
                .andExpect(jsonPath("$.hasNext").value(true));
    }

    @Test
    void queryOmitted_findAllMyGiftProducts_usesSameDefaultsAsPersonalProducts() throws Exception {
        GiftProductSearchCondition condition = new GiftProductSearchCondition(0L, 99999999L, null, null, 20);
        given(giftProductService.findAllMyGiftProducts(USER_ID, condition))
                .willReturn(new ProductCursorPageResponse<>(
                        List.of(), new PriceRangeResponse(null, null), null, false));

        mockMvc.perform(get(URL).with(authenticatedUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void invalidInput_findAllMyGiftProducts_returnsUnprocessableContent() throws Exception {
        mockMvc.perform(get(URL).param("minPrice", "-1").with(authenticatedUser()))
                .andExpect(status().isUnprocessableContent());
        mockMvc.perform(get(URL).param("size", "51").with(authenticatedUser()))
                .andExpect(status().isUnprocessableContent());

        verify(giftProductService, never()).findAllMyGiftProducts(anyLong(), any());
    }

    @Test
    void unknownCategory_findAllMyGiftProducts_returnsUnprocessableContent() throws Exception {
        GiftProductSearchCondition condition = new GiftProductSearchCondition(0L, 99999999L, "UNKNOWN", null, 20);
        given(giftProductService.findAllMyGiftProducts(USER_ID, condition))
                .willThrow(new BusinessException(CommonErrorCode.COMMON_INVALID_INPUT));

        mockMvc.perform(get(URL).param("category", "UNKNOWN").with(authenticatedUser()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    private static RequestPostProcessor authenticatedUser() {
        return authentication(new UsernamePasswordAuthenticationToken(USER_ID, null, List.of()));
    }
}
