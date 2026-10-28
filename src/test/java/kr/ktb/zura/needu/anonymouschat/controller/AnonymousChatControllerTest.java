package kr.ktb.zura.needu.anonymouschat.controller;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import kr.ktb.zura.needu.anonymouschat.dto.request.AnonymousMessageSearchCondition;
import kr.ktb.zura.needu.anonymouschat.dto.request.AnonymousRoomSearchCondition;
import kr.ktb.zura.needu.anonymouschat.dto.request.CreateAnonymousReportRequest;
import kr.ktb.zura.needu.anonymouschat.dto.response.AnonymousChatLastMessageResponse;
import kr.ktb.zura.needu.anonymouschat.dto.response.AnonymousChatProductResponse;
import kr.ktb.zura.needu.anonymouschat.dto.response.AnonymousChatRoomSummaryResponse;
import kr.ktb.zura.needu.anonymouschat.dto.response.AnonymousMessageListResponse;
import kr.ktb.zura.needu.anonymouschat.dto.response.AnonymousMessageSummaryResponse;
import kr.ktb.zura.needu.anonymouschat.dto.response.AnonymousReportResponse;
import kr.ktb.zura.needu.anonymouschat.dto.response.AnonymousRoomCreatedResponse;
import kr.ktb.zura.needu.anonymouschat.dto.response.AnonymousRoomDetailResponse;
import kr.ktb.zura.needu.anonymouschat.exception.AnonymousChatErrorCode;
import kr.ktb.zura.needu.anonymouschat.service.AnonymousChatService;
import kr.ktb.zura.needu.anonymouschat.service.AnonymousMessageService;
import kr.ktb.zura.needu.anonymouschat.service.AnonymousReportService;
import kr.ktb.zura.needu.anonymouschat.type.AnonymousChatRole;
import kr.ktb.zura.needu.anonymouschat.type.AnonymousMessageSender;
import kr.ktb.zura.needu.anonymouschat.type.AnonymousRoomEndedReason;
import kr.ktb.zura.needu.anonymouschat.type.AnonymousRoomStatus;
import kr.ktb.zura.needu.anonymouschat.type.AnonymousReportReason;
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

import static org.mockito.ArgumentMatchers.any;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AnonymousChatController.class)
class AnonymousChatControllerTest {

    private static final Long USER_ID = 100L;
    private static final Long RECIPIENT_USER_ID = 200L;
    private static final Long PRODUCT_ID = 10L;
    private static final Long ROOM_ID = 1L;
    private static final Long MESSAGE_ID = 501L;
    private static final String ROOM_URL = "/api/v1/anonymous-chats/{roomId}";
    private static final String ROOMS_URL = "/api/v1/anonymous-chats";
    private static final String READ_URL = "/api/v1/anonymous-chats/{roomId}/read";
    private static final String REPORT_URL = "/api/v1/anonymous-chats/{roomId}/reports";
    private static final String REPORT_BODY = """
            {"reason": "ABUSE", "description": "욕설을 보냈습니다."}
            """;
    private static final CreateAnonymousReportRequest REPORT_REQUEST =
            new CreateAnonymousReportRequest(AnonymousReportReason.ABUSE, "욕설을 보냈습니다.");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnonymousChatService anonymousChatService;

    @MockitoBean
    private AnonymousMessageService anonymousMessageService;

    @MockitoBean
    private AnonymousReportService anonymousReportService;

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
    void validRequest_createRoom_returnsCreated() throws Exception {
        given(anonymousChatService.createRoom(USER_ID, RECIPIENT_USER_ID, PRODUCT_ID))
                .willReturn(new AnonymousRoomCreatedResponse(
                        ROOM_ID, OffsetDateTime.parse("2026-10-24T10:00:00+09:00")));

        mockMvc.perform(postCreateRoom("{\"recipientUserId\": 200, \"productId\": 10}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("친구에게 슬쩍 물어봤어요."))
                .andExpect(jsonPath("$.data.roomId").value(ROOM_ID))
                .andExpect(jsonPath("$.data.createdAt").value("2026-10-24T10:00:00+09:00"));

        verify(anonymousChatService).createRoom(USER_ID, RECIPIENT_USER_ID, PRODUCT_ID);
    }

    @Test
    void productIdMissing_createRoom_returnsUnprocessableContent() throws Exception {
        mockMvc.perform(postCreateRoom("{\"recipientUserId\": 200}"))
                .andExpect(status().isUnprocessableContent());

        verify(anonymousChatService, never()).createRoom(anyLong(), anyLong(), anyLong());
    }

    @Test
    void productIdNotPositive_createRoom_returnsUnprocessableContent() throws Exception {
        mockMvc.perform(postCreateRoom("{\"recipientUserId\": 200, \"productId\": 0}"))
                .andExpect(status().isUnprocessableContent());

        verify(anonymousChatService, never()).createRoom(anyLong(), anyLong(), anyLong());
    }

    @Test
    void recipientUserIdMissing_createRoom_returnsUnprocessableContent() throws Exception {
        mockMvc.perform(postCreateRoom("{\"productId\": 10}"))
                .andExpect(status().isUnprocessableContent());

        verify(anonymousChatService, never()).createRoom(anyLong(), anyLong(), anyLong());
    }

    @Test
    void csrfMissing_createRoom_returnsForbidden() throws Exception {
        mockMvc.perform(post(CREATE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipientUserId\": 200, \"productId\": 10}")
                        .with(authenticatedUser()))
                .andExpect(status().isForbidden());

        verify(anonymousChatService, never()).createRoom(anyLong(), anyLong(), anyLong());
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
    void validCondition_findAllMessages_returnsCursorResponse() throws Exception {
        AnonymousMessageSearchCondition condition = new AnonymousMessageSearchCondition("cursor", 2);
        given(anonymousMessageService.findAllMessages(USER_ID, ROOM_ID, condition)).willReturn(
                new AnonymousMessageListResponse("익명의 수달", AnonymousRoomStatus.ENDED,
                        AnonymousRoomEndedReason.PARTNER_LEFT, OffsetDateTime.parse("2026-10-25T08:30:00Z"),
                        new CursorPageResponse<>(List.of(
                        new AnonymousMessageSummaryResponse(101L, AnonymousMessageSender.ME, "안녕하세요", 1,
                                OffsetDateTime.parse("2026-10-24T01:00:00Z")),
                        new AnonymousMessageSummaryResponse(100L, AnonymousMessageSender.OTHER, "반가워요", 0,
                                OffsetDateTime.parse("2026-10-24T00:59:00Z"))
                ), "next-cursor", true)));

        mockMvc.perform(get(MESSAGES_URL, ROOM_ID)
                        .param("cursor", "cursor")
                        .param("size", "2")
                        .with(authenticatedUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("익명대화 메시지를 조회했습니다."))
                .andExpect(jsonPath("$.data.targetName").value("익명의 수달"))
                .andExpect(jsonPath("$.data.roomStatus").value("ENDED"))
                .andExpect(jsonPath("$.data.endedReason").value("PARTNER_LEFT"))
                .andExpect(jsonPath("$.data.endedAt").value("2026-10-25T08:30:00Z"))
                .andExpect(jsonPath("$.data.items[0].messageId").value(101))
                .andExpect(jsonPath("$.data.items[0].sender").value("ME"))
                .andExpect(jsonPath("$.data.items[0].content").value("안녕하세요"))
                .andExpect(jsonPath("$.data.items[0].unreadCount").value(1))
                .andExpect(jsonPath("$.data.items[0].sentAt").value("2026-10-24T01:00:00Z"))
                .andExpect(jsonPath("$.data.items[1].sender").value("OTHER"))
                .andExpect(jsonPath("$.data.items[1].unreadCount").value(0))
                .andExpect(jsonPath("$.nextCursor").value("next-cursor"))
                .andExpect(jsonPath("$.hasNext").value(true));
    }

    @Test
    void sizeMissing_findAllMessages_usesDefaultSize() throws Exception {
        given(anonymousMessageService.findAllMessages(USER_ID, ROOM_ID, new AnonymousMessageSearchCondition(null, 20)))
                .willReturn(new AnonymousMessageListResponse("지호", AnonymousRoomStatus.ACTIVE, null, null,
                        new CursorPageResponse<>(List.of(), null, false)));

        mockMvc.perform(get(MESSAGES_URL, ROOM_ID).with(authenticatedUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roomStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.data.endedReason").isEmpty())
                .andExpect(jsonPath("$.data.endedAt").isEmpty())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.nextCursor").isEmpty())
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void sizeOutOfRange_findAllMessages_returnsUnprocessableContent() throws Exception {
        mockMvc.perform(get(MESSAGES_URL, ROOM_ID)
                        .param("size", "51")
                        .with(authenticatedUser()))
                .andExpect(status().isUnprocessableContent());

        verifyNoInteractions(anonymousMessageService);
    }

    @Test
    void roomIdNotNumber_findAllMessages_returnsBadRequest() throws Exception {
        mockMvc.perform(get(MESSAGES_URL, "abc").with(authenticatedUser()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(anonymousMessageService);
    }

    @Test
    void nonParticipant_findAllMessages_returnsForbidden() throws Exception {
        given(anonymousMessageService.findAllMessages(anyLong(), anyLong(), any()))
                .willThrow(new BusinessException(AnonymousChatErrorCode.ANONYMOUS_CHAT_ROOM_FORBIDDEN));

        mockMvc.perform(get(MESSAGES_URL, ROOM_ID).with(authenticatedUser()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("해당 익명대화에 접근할 수 없습니다."));
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

    @Test
    void validRequest_reportChat_returnsCreated() throws Exception {
        given(anonymousReportService.reportRoom(USER_ID, ROOM_ID, REPORT_REQUEST)).willReturn(
                new AnonymousReportResponse(31L, OffsetDateTime.of(2026, 10, 27, 1, 5, 0, 0, ZoneOffset.UTC)));

        mockMvc.perform(postReport(REPORT_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("익명대화를 신고했습니다."))
                .andExpect(jsonPath("$.data.reportId").value(31))
                .andExpect(jsonPath("$.data.createdAt").value("2026-10-27T01:05:00Z"));
    }

    @Test
    void descriptionMissing_reportChat_returnsCreated() throws Exception {
        mockMvc.perform(postReport("{\"reason\": \"SPAM\"}"))
                .andExpect(status().isCreated());

        verify(anonymousReportService).reportRoom(USER_ID, ROOM_ID,
                new CreateAnonymousReportRequest(AnonymousReportReason.SPAM, null));
    }

    @Test
    void reasonMissing_reportChat_returnsUnprocessableContent() throws Exception {
        mockMvc.perform(postReport("{\"description\": \"욕설을 보냈습니다.\"}"))
                .andExpect(status().isUnprocessableContent());

        verify(anonymousReportService, never()).reportRoom(anyLong(), anyLong(), any());
    }

    @Test
    void descriptionTooLong_reportChat_returnsUnprocessableContent() throws Exception {
        String description = "가".repeat(501);

        mockMvc.perform(postReport("{\"reason\": \"ABUSE\", \"description\": \""
                        + description + "\"}"))
                .andExpect(status().isUnprocessableContent());

        verify(anonymousReportService, never()).reportRoom(anyLong(), anyLong(), any());
    }

    @Test
    void unsupportedReason_reportChat_returnsBadRequest() throws Exception {
        mockMvc.perform(postReport("{\"reason\": \"UNKNOWN\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("요청 형식이 올바르지 않습니다."));

        verify(anonymousReportService, never()).reportRoom(anyLong(), anyLong(), any());
    }

    @Test
    void csrfMissing_reportChat_returnsForbidden() throws Exception {
        mockMvc.perform(post(REPORT_URL, ROOM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REPORT_BODY)
                        .with(authenticatedUser()))
                .andExpect(status().isForbidden());

        verify(anonymousReportService, never()).reportRoom(anyLong(), anyLong(), any());
    }

    @Test
    void nonParticipant_reportChat_returnsForbidden() throws Exception {
        given(anonymousReportService.reportRoom(USER_ID, ROOM_ID, REPORT_REQUEST))
                .willThrow(new BusinessException(AnonymousChatErrorCode.ANONYMOUS_CHAT_ROOM_FORBIDDEN));

        mockMvc.perform(postReport(REPORT_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("해당 익명대화에 접근할 수 없습니다."));
    }

    @Test
    void alreadyReported_reportChat_returnsConflict() throws Exception {
        given(anonymousReportService.reportRoom(USER_ID, ROOM_ID, REPORT_REQUEST))
                .willThrow(new BusinessException(AnonymousChatErrorCode.ANONYMOUS_CHAT_REPORT_DUPLICATED));

        mockMvc.perform(postReport(REPORT_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("이미 신고한 익명대화입니다."));
    }

    private static MockHttpServletRequestBuilder patchRead(String body) {
        return patch(READ_URL, ROOM_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(authenticatedUser())
                .with(csrf());
    }

    private static MockHttpServletRequestBuilder postReport(String body) {
        return post(REPORT_URL, ROOM_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(authenticatedUser())
                .with(csrf());
    }

    private static MockHttpServletRequestBuilder postCreateRoom(String body) {
        return post(CREATE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(authenticatedUser())
                .with(csrf());
    }

    private static RequestPostProcessor authenticatedUser() {
        return authentication(new UsernamePasswordAuthenticationToken(USER_ID, null, List.of()));
    }
}
