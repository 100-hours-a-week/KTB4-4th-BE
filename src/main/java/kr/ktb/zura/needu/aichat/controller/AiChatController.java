package kr.ktb.zura.needu.aichat.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import kr.ktb.zura.needu.aichat.dto.request.PatchAnalyzeMessageRequest;
import kr.ktb.zura.needu.aichat.dto.request.SendMessageRequest;
import kr.ktb.zura.needu.aichat.dto.response.AiChatResponseMessage;
import kr.ktb.zura.needu.aichat.dto.response.AiConversationResponse;
import kr.ktb.zura.needu.aichat.dto.response.AiMessageResponse;
import kr.ktb.zura.needu.aichat.dto.response.AiMessageSummaryResponse;
import kr.ktb.zura.needu.aichat.dto.response.AnalysisResultResponse;
import kr.ktb.zura.needu.aichat.dto.response.ProductRecommendationStatusResponse;
import kr.ktb.zura.needu.aichat.facade.AiChatFacade;
import kr.ktb.zura.needu.aichat.facade.AiConversationStartResult;
import kr.ktb.zura.needu.common.response.ApiResponse;
import kr.ktb.zura.needu.common.response.CursorApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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

    private final AiChatFacade aiChatFacade;

    @PostMapping
    public ResponseEntity<ApiResponse<AiConversationResponse>> startOrResumeConversation(
            @AuthenticationPrincipal Long userId
    ) {
        AiConversationStartResult result = aiChatFacade.startOrResumeConversation(userId);
        if (result.isCreated()) {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.of(AiChatResponseMessage.CONVERSATION_CREATED.getMessage(),
                            result.conversation()));
        }
        return ResponseEntity.ok(ApiResponse.of(AiChatResponseMessage.CONVERSATION_FOUND.getMessage(),
                result.conversation()));
    }

    @GetMapping("/{conversationId}/messages")
    public ResponseEntity<CursorApiResponse<AiMessageSummaryResponse>> findAllMessages(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long conversationId,
            @RequestParam(required = false) String cursor,
            @RequestParam @Min(AiChatPageLimits.MIN_PAGE_SIZE) @Max(AiChatPageLimits.MAX_PAGE_SIZE) int size
    ) {
        return ResponseEntity.ok(CursorApiResponse.of(
                AiChatResponseMessage.MESSAGES_FOUND.getMessage(),
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
        return ResponseEntity.ok(ApiResponse.of(AiChatResponseMessage.MESSAGE_SENT.getMessage(), response));
    }

    @PostMapping("/{conversationId}/analysis")
    public ResponseEntity<ApiResponse<AnalysisResultResponse>> createAnalysis(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long conversationId
    ) {
        AnalysisResultResponse response = aiChatFacade.createAnalysis(userId, conversationId);
        return ResponseEntity.ok(ApiResponse.of(AiChatResponseMessage.ANALYSIS_COMPLETED.getMessage(), response));
    }

    @PatchMapping("/{conversationId}/analysis")
    public ResponseEntity<ApiResponse<AnalysisResultResponse>> patchAnalysis(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long conversationId,
            @Valid @RequestBody PatchAnalyzeMessageRequest request
    ) {
        AnalysisResultResponse response = aiChatFacade.patchAnalyze(userId, conversationId, request);
        return ResponseEntity.ok(ApiResponse.of(AiChatResponseMessage.ANALYSIS_UPDATED.getMessage(), response));
    }

    @PostMapping("/{conversationId}/confirm")
    public ResponseEntity<ApiResponse<ProductRecommendationStatusResponse>> confirmAnalysis(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long conversationId
    ) {
        ProductRecommendationStatusResponse response = aiChatFacade.confirmAnalysis(userId, conversationId);
        return ResponseEntity.ok(ApiResponse.of(AiChatResponseMessage.CONVERSATION_COMPLETED.getMessage(), response));
    }


}
