package kr.ktb.zura.needu.aichat.exception;

import java.util.Arrays;
import java.util.List;

import kr.ktb.zura.needu.common.exception.CommonErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class AiChatErrorCodeTest {

    @Test
    void serviceUnavailableCodes_haveDistinctMessages() {
        List<String> messages = findServiceUnavailableMessages();

        assertThat(messages).doesNotHaveDuplicates();
    }

    @Test
    void serviceUnavailableCodes_doNotUseCommonServiceUnavailableMessage() {
        List<String> messages = findServiceUnavailableMessages();

        assertThat(messages).doesNotContain(CommonErrorCode.COMMON_SERVICE_UNAVAILABLE.getMessage());
    }

    // FE는 SSE 복구 중 429를 일시 오류로 보고 계속 재시도하므로, 하루 한도는 재시도를 멈추는 403이어야 한다
    @Test
    void dailyConversationLimitExceeded_usesForbiddenStatus() {
        assertThat(AiChatErrorCode.AICHAT_DAILY_CONVERSATION_LIMIT_EXCEEDED.getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    // FE는 403 중 동의 필요 응답을 문구로만 구분하므로 두 문구가 같으면 동의 화면으로 잘못 이동한다
    @Test
    void dailyConversationLimitExceeded_hasMessageDifferentFromConsentRequired() {
        assertThat(AiChatErrorCode.AICHAT_DAILY_CONVERSATION_LIMIT_EXCEEDED.getMessage())
                .isNotEqualTo(AiChatErrorCode.AICHAT_CONSENT_REQUIRED.getMessage());
    }

    private List<String> findServiceUnavailableMessages() {
        return Arrays.stream(AiChatErrorCode.values())
                .filter(errorCode -> errorCode.getStatus() == HttpStatus.SERVICE_UNAVAILABLE)
                .map(AiChatErrorCode::getMessage)
                .toList();
    }
}
