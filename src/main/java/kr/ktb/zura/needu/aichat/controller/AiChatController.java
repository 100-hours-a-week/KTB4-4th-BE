package kr.ktb.zura.needu.aichat.controller;

import jakarta.validation.Valid;
import kr.ktb.zura.needu.aichat.dto.request.AiMessageSearchCondition;
import kr.ktb.zura.needu.aichat.dto.request.PatchAnalyzeMessageRequest;
import kr.ktb.zura.needu.aichat.dto.request.SendMessageRequest;
import kr.ktb.zura.needu.aichat.dto.response.AiConversationResponse;
import kr.ktb.zura.needu.aichat.dto.response.AiMessageSummaryResponse;
import kr.ktb.zura.needu.aichat.dto.response.AnalysisResultResponse;
import kr.ktb.zura.needu.aichat.dto.response.ProductRecommendationStatusResponse;
import kr.ktb.zura.needu.aichat.facade.AiChatFacade;
import kr.ktb.zura.needu.aichat.facade.AiConversationStartResult;
import kr.ktb.zura.needu.aichat.facade.AiMessageSendResult;
import kr.ktb.zura.needu.common.response.ApiResponse;
import kr.ktb.zura.needu.common.response.CursorApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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
                    .body(ApiResponse.of(AiChatResponseMessages.CONVERSATION_CREATED,
                            result.conversation()));
        }
        return ResponseEntity.ok(ApiResponse.of(AiChatResponseMessages.CONVERSATION_FOUND,
                result.conversation()));
    }

    @GetMapping("/{conversationId}/messages")
    public ResponseEntity<CursorApiResponse<AiMessageSummaryResponse>> findAllMessages(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long conversationId,
            @Valid AiMessageSearchCondition condition
    ) {
        return ResponseEntity.ok(CursorApiResponse.of(
                AiChatResponseMessages.MESSAGES_FOUND,
                aiChatFacade.findAllMessages(userId, conversationId, condition.cursor(), condition.size())
        ));
    }

    // 연결 전 오류 => HTTP 오류로, 연결 후 오류 => error 이벤트로
    @GetMapping(value = "/{conversationId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> connectStream(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long conversationId
    ) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL,
                        "no-cache, no-store, max-age=0, must-revalidate, no-transform")
                .header("X-Accel-Buffering", "no")
                .body(aiChatFacade.connectStream(userId, conversationId));
    }

    @PostMapping("/{conversationId}/messages")
    public ResponseEntity<ApiResponse<Object>> sendMessage(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long conversationId,
            @Valid @RequestBody SendMessageRequest request
    ) {
        AiMessageSendResult result = aiChatFacade.sendMessage(userId, conversationId, request);
        if (result.isAccepted()) {
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(ApiResponse.of(AiChatResponseMessages.MESSAGE_ACCEPTED, result.accepted()));
        }
        return ResponseEntity.ok(ApiResponse.of(AiChatResponseMessages.MESSAGE_SENT, result.reply()));
    }

    @PostMapping("/{conversationId}/analysis")
    public ResponseEntity<ApiResponse<AnalysisResultResponse>> createAnalysis(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long conversationId
    ) {
        AnalysisResultResponse response = aiChatFacade.createAnalysis(userId, conversationId);
        return ResponseEntity.ok(ApiResponse.of(AiChatResponseMessages.ANALYSIS_COMPLETED, response));
    }

    @PatchMapping("/{conversationId}/analysis")
    public ResponseEntity<ApiResponse<AnalysisResultResponse>> patchAnalysis(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long conversationId,
            @Valid @RequestBody PatchAnalyzeMessageRequest request
    ) {
        AnalysisResultResponse response = aiChatFacade.patchAnalyze(userId, conversationId, request);
        return ResponseEntity.ok(ApiResponse.of(AiChatResponseMessages.ANALYSIS_UPDATED, response));
    }

    @PostMapping("/{conversationId}/confirm")
    public ResponseEntity<ApiResponse<ProductRecommendationStatusResponse>> confirmAnalysis(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long conversationId
    ) {
        ProductRecommendationStatusResponse response = aiChatFacade.confirmAnalysis(userId, conversationId);
        return ResponseEntity.ok(ApiResponse.of(AiChatResponseMessages.CONVERSATION_COMPLETED, response));
    }

}
