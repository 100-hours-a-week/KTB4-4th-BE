package kr.ktb.zura.needu.aichat.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import kr.ktb.zura.needu.aichat.dto.request.SendMessageRequest;
import kr.ktb.zura.needu.aichat.dto.response.AiConversationResponse;
import kr.ktb.zura.needu.aichat.dto.response.AiMessageResponse;
import kr.ktb.zura.needu.aichat.dto.response.AiMessageSummaryResponse;
import kr.ktb.zura.needu.aichat.facade.AiChatFacade;
import kr.ktb.zura.needu.aichat.facade.AiConversationStartResult;
import kr.ktb.zura.needu.common.response.ApiResponse;
import kr.ktb.zura.needu.common.response.CursorApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/ai/conversations")
public class AiChatController {

    private static final String CONVERSATION_FOUND_MESSAGE = "AI 대화를 조회했습니다.";
    private static final String CONVERSATION_CREATED_MESSAGE = "AI 대화를 시작했습니다.";
    private static final String MESSAGE_SENT_MESSAGE = "메시지를 전송했습니다.";
    private static final String MESSAGES_FOUND_MESSAGE = "대화 메시지를 조회했습니다.";
    private static final int MIN_PAGE_SIZE = 1;
    private static final int MAX_PAGE_SIZE = 50;

    private final AiChatFacade aiChatFacade;

    @PostMapping
    public ResponseEntity<ApiResponse<AiConversationResponse>> startOrResumeConversation(
            @AuthenticationPrincipal Long userId
    ) {
        AiConversationStartResult result = aiChatFacade.startOrResumeConversation(userId);
        if (result.isCreated()) {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.of(CONVERSATION_CREATED_MESSAGE, result.conversation()));
        }
        return ResponseEntity.ok(ApiResponse.of(CONVERSATION_FOUND_MESSAGE, result.conversation()));
    }

    @GetMapping("/{conversationId}/messages")
    public ResponseEntity<CursorApiResponse<AiMessageSummaryResponse>> findAllMessages(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long conversationId,
            @RequestParam(required = false) String cursor,
            @RequestParam @Min(MIN_PAGE_SIZE) @Max(MAX_PAGE_SIZE) int size
    ) {
        return ResponseEntity.ok(CursorApiResponse.of(
                MESSAGES_FOUND_MESSAGE,
                aiChatFacade.findAllMessages(userId, conversationId, cursor, size)
        ));
    }

    @PostMapping("/{conversationId}/messages")
    public ResponseEntity<ApiResponse<AiMessageResponse>> sendMessage(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long conversationId,
            @Valid @RequestBody SendMessageRequest request
    ) {
        AiMessageResponse response = aiChatFacade.sendMessage(userId, conversationId, request);
        return ResponseEntity.ok(ApiResponse.of(MESSAGE_SENT_MESSAGE, response));
    }
}
