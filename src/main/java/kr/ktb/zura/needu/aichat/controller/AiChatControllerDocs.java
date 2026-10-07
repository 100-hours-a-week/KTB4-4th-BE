package kr.ktb.zura.needu.aichat.controller;

import java.util.List;
import kr.ktb.zura.needu.aichat.dto.response.RestartRequiredResponse;
import kr.ktb.zura.needu.aichat.exception.AiChatErrorCode;
import kr.ktb.zura.needu.common.openapi.ControllerDocs;
import kr.ktb.zura.needu.common.openapi.OperationDoc;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class AiChatControllerDocs implements ControllerDocs {

    // 대화방 소유자·상태 검증에서 공통으로 발생
    private static final List<AiChatErrorCode> CONVERSATION_ACCESS_ERRORS = List.of(
            AiChatErrorCode.AICHAT_CONVERSATION_FORBIDDEN,
            AiChatErrorCode.AICHAT_CONVERSATION_NOT_FOUND,
            AiChatErrorCode.AICHAT_CONVERSATION_EXPIRED);

    @Override
    public List<OperationDoc> operations() {
        return List.of(
                OperationDoc.of(AiChatController.class, "startOrResumeConversation")
                        .successStatus(HttpStatus.CREATED, HttpStatus.OK)
                        .errors(AiChatErrorCode.AICHAT_CONSENT_REQUIRED)
                        .errors(AiChatErrorCode.AICHAT_CONVERSATION_STARTING, AiChatErrorCode.AICHAT_SERVER_UNAVAILABLE)
                        .build(),
                OperationDoc.of(AiChatController.class, "findAllMessages")
                        .errors(CONVERSATION_ACCESS_ERRORS)
                        .build(),
                OperationDoc.of(AiChatController.class, "connectStream")
                        .errors(CONVERSATION_ACCESS_ERRORS)
                        .errors(AiChatErrorCode.AICHAT_INPUT_LOCKED)
                        .build(),
                OperationDoc.of(AiChatController.class, "sendMessage")
                        .errors(CONVERSATION_ACCESS_ERRORS)
                        .errors(AiChatErrorCode.AICHAT_CONSENT_REQUIRED)
                        .errors(AiChatErrorCode.AICHAT_INPUT_LOCKED, AiChatErrorCode.AICHAT_SERVER_UNAVAILABLE)
                        .build(),
                OperationDoc.of(AiChatController.class, "createAnalysis")
                        .errors(CONVERSATION_ACCESS_ERRORS)
                        .errors(AiChatErrorCode.AICHAT_ANALYSIS_NOT_READY)
                        .error(AiChatErrorCode.AICHAT_PROFILE_TOO_SPARSE, new RestartRequiredResponse(true))
                        .errors(AiChatErrorCode.AICHAT_SERVER_UNAVAILABLE)
                        .build(),
                OperationDoc.of(AiChatController.class, "patchAnalysis")
                        .errors(CONVERSATION_ACCESS_ERRORS)
                        .errors(AiChatErrorCode.AICHAT_SERVER_UNAVAILABLE)
                        .build(),
                OperationDoc.of(AiChatController.class, "confirmAnalysis")
                        .errors(CONVERSATION_ACCESS_ERRORS)
                        .errors(AiChatErrorCode.AICHAT_SERVER_UNAVAILABLE)
                        .build()
        );
    }
}
