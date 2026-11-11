package kr.ktb.zura.needu.auth.config;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

// 카카오 계정 없이 서비스를 체험해야 하는 외부 평가자용 테스트 계정. 비밀번호는 BCrypt 해시만 받는다.
@ConfigurationProperties(prefix = "auth.demo-login")
public record DemoLoginProperties(boolean enabled, List<Account> accounts) {

    public DemoLoginProperties {
        accounts = accounts == null ? List.of() : List.copyOf(accounts);
        if (enabled && accounts.isEmpty()) {
            throw new IllegalArgumentException("Demo login accounts must be configured when demo login is enabled");
        }
        Set<String> loginIds = new HashSet<>();
        Set<Long> userIds = new HashSet<>();
        for (Account account : accounts) {
            if (!loginIds.add(account.loginId()) || !userIds.add(account.userId())) {
                throw new IllegalArgumentException("Demo login accounts must have unique login ids and user ids");
            }
        }
    }

    public record Account(String loginId, String passwordHash, Long userId) {

        public Account {
            if (loginId == null || loginId.isBlank()) {
                throw new IllegalArgumentException("Demo login id must not be blank");
            }
            if (passwordHash == null || passwordHash.isBlank()) {
                throw new IllegalArgumentException("Demo login password hash must not be blank");
            }
            if (userId == null || userId <= 0) {
                throw new IllegalArgumentException("Demo login user id must be positive");
            }
        }
    }
}
