package kr.ktb.zura.needu.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class KakaoTokenCipherTest {

    private static final String KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    @Test
    void token_encryptAndDecrypt_returnsOriginalToken() {
        KakaoTokenCipher cipher = new KakaoTokenCipher(KEY);

        String encrypted = cipher.encrypt("kakao-access-token");

        assertNotEquals("kakao-access-token", encrypted);
        assertEquals("kakao-access-token", cipher.decrypt(encrypted));
    }
}
