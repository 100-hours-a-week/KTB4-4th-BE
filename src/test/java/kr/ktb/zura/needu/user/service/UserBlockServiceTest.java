package kr.ktb.zura.needu.user.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import kr.ktb.zura.needu.auth.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserBlockServiceTest {

    private static final Long USER_ID = 1L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 27, 1, 0);

    private UserService userService;
    private AuthService authService;
    private UserBlockService userBlockService;

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        authService = mock(AuthService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-10-27T01:00:00Z"), ZoneOffset.UTC);
        userBlockService = new UserBlockService(userService, authService, clock);
    }

    @Test
    void activeUser_blockFor_blocksAndRevokesSessions() {
        when(userService.blockUntil(USER_ID, NOW, NOW.plusDays(3))).thenReturn(true);

        userBlockService.blockFor(USER_ID, Duration.ofDays(3));

        verify(userService).blockUntil(USER_ID, NOW, NOW.plusDays(3));
        verify(authService).revokeSessionsOfBlockedUser(USER_ID);
    }

    @Test
    void alreadyBlockedUser_blockFor_keepsSessions() {
        when(userService.blockUntil(USER_ID, NOW, NOW.plusDays(3))).thenReturn(false);

        userBlockService.blockFor(USER_ID, Duration.ofDays(3));

        verify(authService, never()).revokeSessionsOfBlockedUser(anyLong());
    }
}
