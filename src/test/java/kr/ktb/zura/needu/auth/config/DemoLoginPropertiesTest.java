package kr.ktb.zura.needu.auth.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class DemoLoginPropertiesTest {

    @Test
    void disabledWithoutAccounts_create_succeeds() {
        assertDoesNotThrow(() -> new DemoLoginProperties(false, null));
    }

    @Test
    void enabledWithoutAccounts_create_throws() {
        assertThrows(IllegalArgumentException.class, () -> new DemoLoginProperties(true, List.of()));
    }

    @Test
    void duplicateLoginId_create_throws() {
        assertThrows(IllegalArgumentException.class, () -> new DemoLoginProperties(true, List.of(
                new DemoLoginProperties.Account("reviewer", "hash", 1L),
                new DemoLoginProperties.Account("reviewer", "hash", 2L))));
    }

    @Test
    void duplicateUserId_create_throws() {
        assertThrows(IllegalArgumentException.class, () -> new DemoLoginProperties(true, List.of(
                new DemoLoginProperties.Account("reviewer1", "hash", 1L),
                new DemoLoginProperties.Account("reviewer2", "hash", 1L))));
    }

    @Test
    void blankPasswordHash_createAccount_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new DemoLoginProperties.Account("reviewer", " ", 1L));
    }
}
