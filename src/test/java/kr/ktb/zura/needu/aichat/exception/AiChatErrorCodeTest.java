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

    private List<String> findServiceUnavailableMessages() {
        return Arrays.stream(AiChatErrorCode.values())
                .filter(errorCode -> errorCode.getStatus() == HttpStatus.SERVICE_UNAVAILABLE)
                .map(AiChatErrorCode::getMessage)
                .toList();
    }
}
