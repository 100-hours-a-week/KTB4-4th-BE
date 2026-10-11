package kr.ktb.zura.needu.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.response.RetryAfterResponse;
import kr.ktb.zura.needu.notification.exception.NotificationErrorCode;
import kr.ktb.zura.needu.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class NotificationStreamServiceTest {

    private static final Long USER_ID = 1L;

    private final UserService userService = mock(UserService.class);
    private final NotificationStreamService notificationStreamService =
            new NotificationStreamService(userService, Duration.ofMinutes(30));

    @Test
    void activeUser_connect_returnsEmitter() {
        SseEmitter emitter = notificationStreamService.connect(USER_ID);

        assertThat(emitter).isNotNull();
        assertThat(emitter.getTimeout()).isEqualTo(Duration.ofMinutes(30).toMillis());
        verify(userService).validateActiveUser(USER_ID);
    }

    @Test
    void fiveStreamsAlreadyConnected_connect_throwsTooManyRequests() {
        for (int index = 0; index < 5; index++) {
            notificationStreamService.connect(USER_ID);
        }

        assertThatThrownBy(() -> notificationStreamService.connect(USER_ID))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode())
                            .isEqualTo(NotificationErrorCode.NOTIFICATION_STREAM_LIMIT_EXCEEDED);
                    assertThat(exception.getData()).isEqualTo(new RetryAfterResponse(10));
                });
    }
}
