package kr.ktb.zura.needu.auth.service;

import java.util.Optional;
import java.util.UUID;
import kr.ktb.zura.needu.auth.config.DemoLoginProperties;
import kr.ktb.zura.needu.auth.exception.AuthErrorCode;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.ratelimit.EventRateLimiter;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class DemoAccountService {

    static final String LOGIN_RATE_LIMIT_EVENT = "auth-demo-login";

    private final DemoLoginProperties properties;
    private final PasswordEncoder passwordEncoder;
    private final EventRateLimiter eventRateLimiter;
    // 없는 아이디도 비밀번호 비교 시간을 들여 응답 시간으로 아이디 존재 여부를 알 수 없게 한다.
    private final String unknownAccountPasswordHash;

    public DemoAccountService(DemoLoginProperties properties, PasswordEncoder passwordEncoder,
                              EventRateLimiter eventRateLimiter) {
        this.properties = properties;
        this.passwordEncoder = passwordEncoder;
        this.eventRateLimiter = eventRateLimiter;
        this.unknownAccountPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    public Long authenticate(String loginId, String password) {
        if (!properties.enabled()) {
            throw new BusinessException(AuthErrorCode.AUTH_DEMO_LOGIN_DISABLED);
        }
        Optional<DemoLoginProperties.Account> account = findAccount(loginId);
        if (account.isEmpty()) {
            passwordEncoder.matches(password, unknownAccountPasswordHash);
            throw new BusinessException(AuthErrorCode.AUTH_DEMO_LOGIN_FAILED);
        }
        eventRateLimiter.validateWithinLimit(LOGIN_RATE_LIMIT_EVENT, account.get().userId());
        if (!passwordEncoder.matches(password, account.get().passwordHash())) {
            throw new BusinessException(AuthErrorCode.AUTH_DEMO_LOGIN_FAILED);
        }
        return account.get().userId();
    }

    // 로그인을 끈 뒤에도 카카오 계정이 없는 테스트 계정은 탈퇴 등에서 구분해야 하므로 enabled와 무관하게 판단한다.
    public boolean isDemoAccount(Long userId) {
        return properties.accounts().stream()
                .anyMatch(account -> account.userId().equals(userId));
    }

    private Optional<DemoLoginProperties.Account> findAccount(String loginId) {
        return properties.accounts().stream()
                .filter(account -> account.loginId().equals(loginId))
                .findFirst();
    }
}
