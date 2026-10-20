package kr.ktb.zura.needu.aichat.controller;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import kr.ktb.zura.needu.aichat.dto.response.DailyConversationLimitResponse;
import kr.ktb.zura.needu.aichat.dto.response.RestartRequiredResponse;
import kr.ktb.zura.needu.aichat.exception.AiChatErrorCode;
import kr.ktb.zura.needu.common.openapi.ControllerDocs;
import kr.ktb.zura.needu.common.openapi.OperationDoc;
import kr.ktb.zura.needu.user.exception.UserErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class AiChatControllerDocs implements ControllerDocs {

    // 대화방 소유자·상태 검증에서 공통으로 발생
    private static final List<AiChatErrorCode> CONVERSATION_ACCESS_ERRORS = List.of(
            AiChatErrorCode.AICHAT_CONVERSATION_FORBIDDEN,
            AiChatErrorCode.AICHAT_CONVERSATION_NOT_FOUND,
            AiChatErrorCode.AICHAT_CONVERSATION_EXPIRED);
    private static final List<UserErrorCode> ACTIVE_USER_ERRORS = List.of(
            UserErrorCode.USER_BLOCKED,
            UserErrorCode.USER_ONBOARDING_REQUIRED);
    // AI 서버를 동기로 호출하는 API에서 발생. 모두 503이지만 문구가 원인별로 달라 각각 예시로 남긴다
    private static final List<AiChatErrorCode> AI_SERVER_ERRORS = List.of(
            AiChatErrorCode.AICHAT_SERVER_UNAVAILABLE,
            AiChatErrorCode.AICHAT_REQUEST_TIMEOUT,
            AiChatErrorCode.AICHAT_INVALID_RESPONSE,
            AiChatErrorCode.AICHAT_REQUEST_REJECTED);

    @Override
    public List<OperationDoc> operations() {
        return List.of(
                OperationDoc.of(AiChatController.class, "startOrResumeConversation")
                        .successStatus(HttpStatus.CREATED, HttpStatus.OK)
                        .errors(ACTIVE_USER_ERRORS)
                        .errors(AiChatErrorCode.AICHAT_CONSENT_REQUIRED)
                        .error(AiChatErrorCode.AICHAT_DAILY_CONVERSATION_LIMIT_EXCEEDED,
                                new DailyConversationLimitResponse(OffsetDateTime.of(2026, 10, 20, 15, 0, 0, 0, ZoneOffset.UTC)))
                        .errors(AiChatErrorCode.AICHAT_CONVERSATION_STARTING)
                        .errors(AI_SERVER_ERRORS)
                        .build(),
                OperationDoc.of(AiChatController.class, "findAllMessages")
                        .errors(ACTIVE_USER_ERRORS)
                        .errors(CONVERSATION_ACCESS_ERRORS)
                        .build(),
                OperationDoc.of(AiChatController.class, "connectStream")
                        .errors(ACTIVE_USER_ERRORS)
                        .errors(CONVERSATION_ACCESS_ERRORS)
                        .errors(AiChatErrorCode.AICHAT_INPUT_LOCKED)
                        .build(),
                // AI 서버 오류는 응답이 아니라 스트림의 error 이벤트로 전달된다
                // 429(이전 답변 생성 중)·503(동시 답변 수 초과)은 요청 제한 정책의 공통 오류와 문구가 같아 함께 문서화된다
                OperationDoc.of(AiChatController.class, "sendMessage")
                        .successStatus(HttpStatus.ACCEPTED, HttpStatus.OK)
                        .errors(ACTIVE_USER_ERRORS)
                        .errors(CONVERSATION_ACCESS_ERRORS)
                        .errors(AiChatErrorCode.AICHAT_CONSENT_REQUIRED, AiChatErrorCode.AICHAT_INPUT_LOCKED)
                        .build(),
                OperationDoc.of(AiChatController.class, "createAnalysis")
                        .errors(ACTIVE_USER_ERRORS)
                        .errors(CONVERSATION_ACCESS_ERRORS)
                        .errors(AiChatErrorCode.AICHAT_ANALYSIS_NOT_READY)
                        .error(AiChatErrorCode.AICHAT_PROFILE_TOO_SPARSE, new RestartRequiredResponse(true))
                        .errors(AI_SERVER_ERRORS)
                        .build(),
                OperationDoc.of(AiChatController.class, "patchAnalysis")
                        .errors(ACTIVE_USER_ERRORS)
                        .errors(CONVERSATION_ACCESS_ERRORS)
                        .errors(AI_SERVER_ERRORS)
                        .build(),
                OperationDoc.of(AiChatController.class, "confirmAnalysis")
                        .errors(ACTIVE_USER_ERRORS)
                        .errors(CONVERSATION_ACCESS_ERRORS)
                        .errors(AI_SERVER_ERRORS)
                        .build()
        );
    }
}
