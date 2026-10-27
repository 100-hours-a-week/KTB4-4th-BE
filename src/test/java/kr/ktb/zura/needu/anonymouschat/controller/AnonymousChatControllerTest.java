package kr.ktb.zura.needu.anonymouschat.controller;

import java.util.List;

import kr.ktb.zura.needu.anonymouschat.dto.response.AnonymousRoomDetailResponse;
import kr.ktb.zura.needu.anonymouschat.exception.AnonymousChatErrorCode;
import kr.ktb.zura.needu.anonymouschat.service.AnonymousChatService;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.product.dto.response.ProductSummaryResponse;
import kr.ktb.zura.needu.product.type.ProductCategory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AnonymousChatController.class)
class AnonymousChatControllerTest {

    private static final Long USER_ID = 100L;
    private static final Long ROOM_ID = 1L;
    private static final String ROOM_URL = "/api/v1/anonymous-chats/{roomId}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnonymousChatService anonymousChatService;

    @Test
    void participant_findRoom_returnsRoomDetail() throws Exception {
        given(anonymousChatService.findRoom(USER_ID, ROOM_ID)).willReturn(new AnonymousRoomDetailResponse(
                ROOM_ID,
                true,
                "지호",
                new ProductSummaryResponse(10L, "무선 헤드폰", 129000L, ProductCategory.DIGITAL,
                        "https://example.com/product.png")
        ));

        mockMvc.perform(get(ROOM_URL, ROOM_ID).with(authenticatedUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("익명대화방 정보를 조회했습니다."))
                .andExpect(jsonPath("$.data.roomId").value(ROOM_ID))
                .andExpect(jsonPath("$.data.askedByMe").value(true))
                .andExpect(jsonPath("$.data.targetName").value("지호"))
                .andExpect(jsonPath("$.data.product.productId").value(10))
                .andExpect(jsonPath("$.data.product.name").value("무선 헤드폰"))
                .andExpect(jsonPath("$.data.product.price").value(129000))
                .andExpect(jsonPath("$.data.product.category").value("DIGITAL"))
                .andExpect(jsonPath("$.data.product.imageUrl").value("https://example.com/product.png"))
                .andExpect(jsonPath("$.data.createdAt").doesNotExist());
    }

    @Test
    void roomIdNotNumber_findRoom_returnsBadRequest() throws Exception {
        mockMvc.perform(get(ROOM_URL, "abc").with(authenticatedUser()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("요청 형식이 올바르지 않습니다."));

        verify(anonymousChatService, never()).findRoom(anyLong(), anyLong());
    }

    @Test
    void nonParticipant_findRoom_returnsForbidden() throws Exception {
        given(anonymousChatService.findRoom(USER_ID, ROOM_ID))
                .willThrow(new BusinessException(AnonymousChatErrorCode.ANONYMOUS_CHAT_ROOM_FORBIDDEN));

        mockMvc.perform(get(ROOM_URL, ROOM_ID).with(authenticatedUser()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("해당 익명대화에 접근할 수 없습니다."))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void missingRoom_findRoom_returnsNotFound() throws Exception {
        given(anonymousChatService.findRoom(USER_ID, ROOM_ID))
                .willThrow(new BusinessException(AnonymousChatErrorCode.ANONYMOUS_CHAT_ROOM_NOT_FOUND));

        mockMvc.perform(get(ROOM_URL, ROOM_ID).with(authenticatedUser()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("익명대화방을 찾을 수 없습니다."));
    }

    private static RequestPostProcessor authenticatedUser() {
        return authentication(new UsernamePasswordAuthenticationToken(USER_ID, null, List.of()));
    }
}
