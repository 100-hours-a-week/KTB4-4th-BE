package kr.ktb.zura.needu.common.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtConfigTest {

    private final JwtConfig jwtConfig = new JwtConfig();

    @Test
    void invalidSecret_failsConfiguration() {
        assertThrows(IllegalStateException.class, () -> jwtConfig.jwtSecretKey("not-base64"));
        assertThrows(IllegalStateException.class, () -> jwtConfig.jwtSecretKey("c2hvcnQ="));
    }
}
