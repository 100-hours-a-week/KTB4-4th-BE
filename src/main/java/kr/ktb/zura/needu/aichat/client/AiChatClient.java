package kr.ktb.zura.needu.aichat.client;

import kr.ktb.zura.needu.aichat.client.dto.request.AiServerPatchAnalysisRequest;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerAnalysisResponse;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerCloseSessionResponse;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerSendMessageResponse;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerStartSessionResponse;

public interface AiChatClient {

    AiServerStartSessionResponse startSession(Long userId, Long conversationRoomId);

    AiServerSendMessageResponse sendMessage(Long userId, Long conversationRoomId, String message);

    AiServerAnalysisResponse createAnalysis(Long conversationRoomId);

    AiServerAnalysisResponse patchAnalyze(Long conversationRoomId, AiServerPatchAnalysisRequest request);

    AiServerCloseSessionResponse confirmAnalysis(Long userId, Long conversationRoomId);
}
