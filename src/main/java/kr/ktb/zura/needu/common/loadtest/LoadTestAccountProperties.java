package kr.ktb.zura.needu.common.loadtest;

import org.springframework.boot.context.properties.ConfigurationProperties;

// 부하 테스트 계정의 user id 범위. 두 값이 모두 있을 때만 켜지고, 평소에는 비워 둔다.
@ConfigurationProperties(prefix = "needu.load-test-account")
public record LoadTestAccountProperties(Long userIdFrom, Long userIdTo) {

    public LoadTestAccountProperties {
        if ((userIdFrom == null) != (userIdTo == null)) {
            throw new IllegalArgumentException("Load test account user id range requires both from and to");
        }
        if (userIdFrom != null && (userIdFrom <= 0 || userIdFrom > userIdTo)) {
            throw new IllegalArgumentException("Load test account user id range is invalid");
        }
    }

    public static LoadTestAccountProperties disabled() {
        return new LoadTestAccountProperties(null, null);
    }

    public boolean isLoadTestAccount(Long userId) {
        return userIdFrom != null
                && userId != null
                && userId >= userIdFrom
                && userId <= userIdTo;
    }
}
