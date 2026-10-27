package kr.ktb.zura.needu.anonymouschat.controller;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

import kr.ktb.zura.needu.anonymouschat.dto.request.AnonymousRoomSearchCondition;
import kr.ktb.zura.needu.anonymouschat.dto.response.AnonymousChatLastMessageResponse;
import kr.ktb.zura.needu.anonymouschat.dto.response.AnonymousChatProductResponse;
import kr.ktb.zura.needu.anonymouschat.dto.response.AnonymousChatRoomSummaryResponse;
import kr.ktb.zura.needu.anonymouschat.dto.response.AnonymousRoomDetailResponse;
import kr.ktb.zura.needu.anonymouschat.exception.AnonymousChatErrorCode;
import kr.ktb.zura.needu.anonymouschat.service.AnonymousChatService;
import kr.ktb.zura.needu.anonymouschat.service.AnonymousMessageService;
import kr.ktb.zura.needu.anonymouschat.type.AnonymousChatRole;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.CommonErrorCode;
import kr.ktb.zura.needu.common.response.CursorPageResponse;
import kr.ktb.zura.needu.product.dto.response.ProductSummaryResponse;
import kr.ktb.zura.needu.product.type.ProductCategory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AnonymousChatController.class)
class AnonymousChatControllerTest {

    private static final Long USER_ID = 100L;
    private static final Long ROOM_ID = 1L;
    private static final Long MESSAGE_ID = 501L;
    private static final String ROOM_URL = "/api/v1/anonymous-chats/{roomId}";
    private static final String ROOMS_URL = "/api/v1/anonymous-chats";
    private static final String READ_URL = "/api/v1/anonymous-chats/{roomId}/read";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnonymousChatService anonymousChatService;

    @MockitoBean
    private AnonymousMessageService anonymousMessageService;

    @Test
    void validCondition_findAllRooms_returnsCursorResponse() throws Exception {
        AnonymousRoomSearchCondition condition = new AnonymousRoomSearchCondition(
                AnonymousChatRole.RESPONDER, null, 20);
        given(anonymousChatService.findAllRooms(USER_ID, condition)).willReturn(new CursorPageResponse<>(
                List.of(new AnonymousChatRoomSummaryResponse(
                        3L,
                        "익명의 수달",
                        true,
                        3L,
                        LocalDateTime.parse("2026-10-24T15:30:00"),
                        new AnonymousChatLastMessageResponse(
                                305L, "이 상품 선물로 괜찮을까요?", "OTHER",
                                OffsetDateTime.parse("2026-10-24T06:30:00Z")),
                        new AnonymousChatProductResponse(30L, "무선 헤드폰")
                )),
                "next-cursor",
                true
        ));

        mockMvc.perform(get(ROOMS_URL)
                        .param("role", "RESPONDER")
                        .param("size", "20")
                        .with(authenticatedUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("익명대화방 목록을 조회했습니다."))
                .andExpect(jsonPath("$.data.items[0].roomId").value(3))
                .andExpect(jsonPath("$.data.items[0].counterpartAlias").value("익명의 수달"))
                .andExpect(jsonPath("$.data.items[0].unreadMessageCount").value(3))
                .andExpect(jsonPath("$.data.items[0].lastActiveAt").value("2026-10-24T15:30:00"))
                .andExpect(jsonPath("$.data.items[0].lastMessage.sender").value("OTHER"))
                .andExpect(jsonPath("$.data.items[0].product.name").value("무선 헤드폰"))
                .andExpect(jsonPath("$.nextCursor").value("next-cursor"))
                .andExpect(jsonPath("$.hasNext").value(true));
    }

    @Test
    void invalidRole_findAllRooms_returnsBadRequest() throws Exception {
        mockMvc.perform(get(ROOMS_URL)
                        .param("role", "UNKNOWN")
                        .param("size", "20")
                        .with(authenticatedUser()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("요청 형식이 올바르지 않습니다."));

        verifyNoInteractions(anonymousChatService);
    }

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

    @Test
    void validRequest_readMessages_returnsNoContent() throws Exception {
        mockMvc.perform(patchRead("{\"lastReadMessageId\": 501}"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(anonymousMessageService).readMessages(USER_ID, ROOM_ID, MESSAGE_ID);
    }

    @Test
    void lastReadMessageIdMissing_readMessages_returnsUnprocessableContent() throws Exception {
        mockMvc.perform(patchRead("{}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("입력 값이 유효하지 않습니다. 입력한 내용을 확인해 주세요."));

        verify(anonymousMessageService, never()).readMessages(anyLong(), anyLong(), anyLong());
    }

    @Test
    void lastReadMessageIdNotNumber_readMessages_returnsBadRequest() throws Exception {
        mockMvc.perform(patchRead("{\"lastReadMessageId\": \"abc\"}"))
                .andExpect(status().isBadRequest());

        verify(anonymousMessageService, never()).readMessages(anyLong(), anyLong(), anyLong());
    }

    @Test
    void csrfMissing_readMessages_returnsForbidden() throws Exception {
        mockMvc.perform(patch(READ_URL, ROOM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lastReadMessageId\": 501}")
                        .with(authenticatedUser()))
                .andExpect(status().isForbidden());

        verify(anonymousMessageService, never()).readMessages(anyLong(), anyLong(), anyLong());
    }

    @Test
    void messageNotInRoom_readMessages_returnsUnprocessableContent() throws Exception {
        willThrow(new BusinessException(CommonErrorCode.COMMON_INVALID_INPUT))
                .given(anonymousMessageService).readMessages(USER_ID, ROOM_ID, MESSAGE_ID);

        mockMvc.perform(patchRead("{\"lastReadMessageId\": 501}"))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void nonParticipant_readMessages_returnsForbidden() throws Exception {
        willThrow(new BusinessException(AnonymousChatErrorCode.ANONYMOUS_CHAT_ROOM_FORBIDDEN))
                .given(anonymousMessageService).readMessages(USER_ID, ROOM_ID, MESSAGE_ID);

        mockMvc.perform(patchRead("{\"lastReadMessageId\": 501}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("해당 익명대화에 접근할 수 없습니다."));
    }

    private static MockHttpServletRequestBuilder patchRead(String body) {
        return patch(READ_URL, ROOM_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(authenticatedUser())
                .with(csrf());
    }

    private static RequestPostProcessor authenticatedUser() {
        return authentication(new UsernamePasswordAuthenticationToken(USER_ID, null, List.of()));
    }
}
