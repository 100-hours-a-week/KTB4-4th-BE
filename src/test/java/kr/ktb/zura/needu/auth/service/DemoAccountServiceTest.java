package kr.ktb.zura.needu.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import kr.ktb.zura.needu.auth.config.DemoLoginProperties;
import kr.ktb.zura.needu.auth.exception.AuthErrorCode;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.TooManyRequestsException;
import kr.ktb.zura.needu.common.ratelimit.EventRateLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class DemoAccountServiceTest {

    private static final Long DEMO_USER_ID = 101L;
    private static final String LOGIN_ID = "reviewer1";
    private static final String PASSWORD = "demo-password";

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private final EventRateLimiter eventRateLimiter = mock(EventRateLimiter.class);

    @Test
    void validCredentials_authenticate_returnsDemoUserId() {
        DemoAccountService service = service(true);

        Long userId = service.authenticate(LOGIN_ID, PASSWORD);

        assertEquals(DEMO_USER_ID, userId);
        verify(eventRateLimiter).validateWithinLimit(DemoAccountService.LOGIN_RATE_LIMIT_EVENT, DEMO_USER_ID);
    }

    @Test
    void wrongPassword_authenticate_throwsLoginFailed() {
        DemoAccountService service = service(true);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.authenticate(LOGIN_ID, "wrong-password"));

        assertEquals(AuthErrorCode.AUTH_DEMO_LOGIN_FAILED, exception.getErrorCode());
    }

    @Test
    void unknownLoginId_authenticate_throwsLoginFailedWithoutCountingRateLimit() {
        DemoAccountService service = service(true);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.authenticate("unknown", PASSWORD));

        assertEquals(AuthErrorCode.AUTH_DEMO_LOGIN_FAILED, exception.getErrorCode());
        verify(eventRateLimiter, never()).validateWithinLimit(anyString(), anyLong());
    }

    @Test
    void disabledDemoLogin_authenticate_throwsDisabled() {
        DemoAccountService service = service(false);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.authenticate(LOGIN_ID, PASSWORD));

        assertEquals(AuthErrorCode.AUTH_DEMO_LOGIN_DISABLED, exception.getErrorCode());
    }

    @Test
    void rateLimitExceeded_authenticate_throwsBeforePasswordCheck() {
        DemoAccountService service = service(true);
        doThrow(new TooManyRequestsException(30)).when(eventRateLimiter)
                .validateWithinLimit(DemoAccountService.LOGIN_RATE_LIMIT_EVENT, DEMO_USER_ID);

        assertThrows(TooManyRequestsException.class, () -> service.authenticate(LOGIN_ID, PASSWORD));
    }

    @Test
    void configuredUserIdEvenWhenDisabled_isDemoAccount_returnsTrue() {
        DemoAccountService service = service(false);

        assertTrue(service.isDemoAccount(DEMO_USER_ID));
        assertFalse(service.isDemoAccount(1L));
    }

    private DemoAccountService service(boolean enabled) {
        DemoLoginProperties properties = new DemoLoginProperties(enabled, List.of(
                new DemoLoginProperties.Account(LOGIN_ID, passwordEncoder.encode(PASSWORD), DEMO_USER_ID)));
        return new DemoAccountService(properties, passwordEncoder, eventRateLimiter);
    }
}
