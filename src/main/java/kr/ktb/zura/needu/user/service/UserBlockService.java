package kr.ktb.zura.needu.user.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import kr.ktb.zura.needu.auth.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserBlockService {

    private final UserService userService;
    private final AuthService authService;
    private final Clock clock;

    @Transactional
    public void blockFor(Long userId, Duration duration) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime blockedUntil = now.plus(duration);
        if (!userService.blockUntil(userId, now, blockedUntil)) {
            return;
        }
        authService.revokeSessionsOfBlockedUser(userId);
        log.info("User blocked. userId={}, blockedUntil={}", userId, blockedUntil);
    }
}
