package kr.ktb.zura.needu.product.controller;

import java.util.List;
import kr.ktb.zura.needu.product.dto.request.CreateProductLinkClickRequest;
import kr.ktb.zura.needu.product.service.ProductLinkClickService;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductLinkClickController.class)
class ProductLinkClickControllerTest {

    private static final Long LOGIN_USER_ID = 1L;
    private static final String URL = "/api/v1/analytics/product-link-clicks";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductLinkClickService productLinkClickService;

    @Test
    void friendGiftWithFriendUserId_createLinkClick_returnsNoContent() throws Exception {
        mockMvc.perform(post(URL).with(authenticatedUser()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\": 2992, \"context\": \"FRIEND_GIFT\", \"friendUserId\": 321, "
                                + "\"recommendationId\": 55}"))
                .andExpect(status().isNoContent());

        verify(productLinkClickService).createLinkClick(
                LOGIN_USER_ID, new CreateProductLinkClickRequest(2992L, "FRIEND_GIFT", 321L, 55L));
    }

    @Test
    void friendGiftWithoutFriendUserId_createLinkClick_returnsUnprocessableContent() throws Exception {
        mockMvc.perform(post(URL).with(authenticatedUser()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\": 2992, \"context\": \"FRIEND_GIFT\"}"))
                .andExpect(status().isUnprocessableContent());

        verify(productLinkClickService, never()).createLinkClick(anyLong(), any());
    }

    @Test
    void personalWithoutFriendUserId_createLinkClick_returnsNoContent() throws Exception {
        mockMvc.perform(post(URL).with(authenticatedUser()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\": 2992, \"context\": \"PERSONAL\"}"))
                .andExpect(status().isNoContent());
    }

    private static RequestPostProcessor authenticatedUser() {
        return authentication(new UsernamePasswordAuthenticationToken(LOGIN_USER_ID, null, List.of()));
    }
}
