package kr.ktb.zura.needu.common.config;

import kr.ktb.zura.needu.common.loadtest.LoadTestAccountProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class LoadTestAccountConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(LoadTestAccountConfig.class);

    @Test
    void emptyRange_bind_disablesLoadTestAccounts() {
        contextRunner
                .withPropertyValues(
                        "needu.load-test-account.user-id-from=",
                        "needu.load-test-account.user-id-to=")
                .run(context -> {
                    LoadTestAccountProperties properties = context.getBean(LoadTestAccountProperties.class);
                    assertThat(properties.isLoadTestAccount(1L)).isFalse();
                });
    }

    @Test
    void range_bind_matchesOnlyUsersInRange() {
        contextRunner
                .withPropertyValues(
                        "needu.load-test-account.user-id-from=100",
                        "needu.load-test-account.user-id-to=200")
                .run(context -> {
                    LoadTestAccountProperties properties = context.getBean(LoadTestAccountProperties.class);
                    assertThat(properties.isLoadTestAccount(99L)).isFalse();
                    assertThat(properties.isLoadTestAccount(100L)).isTrue();
                    assertThat(properties.isLoadTestAccount(200L)).isTrue();
                    assertThat(properties.isLoadTestAccount(201L)).isFalse();
                });
    }

    @Test
    void onlyStartOfRange_bind_failsToStart() {
        contextRunner
                .withPropertyValues("needu.load-test-account.user-id-from=100")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void reversedRange_bind_failsToStart() {
        contextRunner
                .withPropertyValues(
                        "needu.load-test-account.user-id-from=200",
                        "needu.load-test-account.user-id-to=100")
                .run(context -> assertThat(context).hasFailed());
    }
}
