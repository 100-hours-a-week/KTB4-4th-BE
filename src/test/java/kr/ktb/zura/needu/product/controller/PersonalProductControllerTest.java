package kr.ktb.zura.needu.product.controller;

import java.util.List;

import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.CommonErrorCode;
import kr.ktb.zura.needu.common.response.CursorPageResponse;
import kr.ktb.zura.needu.product.dto.response.PersonalProductResponse;
import kr.ktb.zura.needu.product.service.PersonalProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PersonalProductController.class)
class PersonalProductControllerTest {

    private static final Long USER_ID = 1L;
    private static final String URL = "/api/v1/users/me/personal-recommendations";
    private static final PersonalProductResponse LAMP =
            new PersonalProductResponse(5001L, 1001L, "미니멀 테이블 램프", "https://image.test/lamp.png", 52000L);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PersonalProductService personalProductService;

    @Test
    void firstPageRequest_findAllPersonalProducts_returnsItemsWithTopLevelCursor() throws Exception {
        given(personalProductService.findAllPersonalProducts(USER_ID, null, 20))
                .willReturn(new CursorPageResponse<>(List.of(LAMP), "next-cursor", true));

        mockMvc.perform(get(URL).with(authenticatedUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("개인 추천 상품 목록을 조회했습니다."))
                .andExpect(jsonPath("$.data.items[0].recommendationId").value(5001))
                .andExpect(jsonPath("$.data.items[0].productId").value(1001))
                .andExpect(jsonPath("$.data.items[0].name").value("미니멀 테이블 램프"))
                .andExpect(jsonPath("$.data.items[0].imageUrl").value("https://image.test/lamp.png"))
                .andExpect(jsonPath("$.data.items[0].price").value(52000))
                .andExpect(jsonPath("$.nextCursor").value("next-cursor"))
                .andExpect(jsonPath("$.hasNext").value(true));
    }

    @Test
    void cursorAndSizeGiven_findAllPersonalProducts_passesThemToService() throws Exception {
        given(personalProductService.findAllPersonalProducts(USER_ID, "cursor", 10))
                .willReturn(new CursorPageResponse<>(List.of(), null, false));

        mockMvc.perform(get(URL).param("cursor", "cursor").param("size", "10").with(authenticatedUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.nextCursor").isEmpty())
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void sizeOutOfRange_findAllPersonalProducts_returnsUnprocessableContent() throws Exception {
        mockMvc.perform(get(URL).param("size", "51").with(authenticatedUser()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("입력값이 유효하지 않습니다. 입력 내용을 확인해 주세요."))
                .andExpect(jsonPath("$.data").isEmpty());

        mockMvc.perform(get(URL).param("size", "0").with(authenticatedUser()))
                .andExpect(status().isUnprocessableContent());

        verify(personalProductService, never()).findAllPersonalProducts(anyLong(), any(), anyInt());
    }

    @Test
    void nonNumericSize_findAllPersonalProducts_returnsBadRequest() throws Exception {
        mockMvc.perform(get(URL).param("size", "abc").with(authenticatedUser()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("요청 형식이 올바르지 않습니다."))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void invalidCursor_findAllPersonalProducts_returnsBadRequest() throws Exception {
        given(personalProductService.findAllPersonalProducts(USER_ID, "invalid", 20))
                .willThrow(new BusinessException(CommonErrorCode.COMMON_INVALID_REQUEST));

        mockMvc.perform(get(URL).param("cursor", "invalid").with(authenticatedUser()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("요청 형식이 올바르지 않습니다."))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void unexpectedException_findAllPersonalProducts_returnsInternalServerError() throws Exception {
        given(personalProductService.findAllPersonalProducts(USER_ID, null, 20)).willThrow(new IllegalStateException());

        mockMvc.perform(get(URL).with(authenticatedUser()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("요청을 처리하지 못했습니다."))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    private static RequestPostProcessor authenticatedUser() {
        return authentication(new UsernamePasswordAuthenticationToken(USER_ID, null, List.of()));
    }
}
