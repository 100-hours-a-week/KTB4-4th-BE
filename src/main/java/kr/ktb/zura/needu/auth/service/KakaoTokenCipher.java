package kr.ktb.zura.needu.auth.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class KakaoTokenCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final SecretKey secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public KakaoTokenCipher(@Value("${kakao.oauth.token-encryption-key}") String encodedSecretKey) {
        byte[] key;
        try {
            key = Base64.getDecoder().decode(encodedSecretKey);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Kakao token encryption key must be Base64 encoded", exception);
        }
        if (key.length != 32) {
            throw new IllegalArgumentException("Kakao token encryption key must be 32 bytes");
        }
        this.secretKey = new SecretKeySpec(key, "AES");
    }

    public String encrypt(String token) {
        byte[] iv = new byte[IV_LENGTH];
        secureRandom.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + ciphertext.length)
                    .put(iv)
                    .put(ciphertext)
                    .array());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Failed to encrypt Kakao token", exception);
        }
    }

    public String decrypt(String encryptedToken) {
        byte[] value = Base64.getDecoder().decode(encryptedToken);
        if (value.length <= IV_LENGTH) {
            throw new IllegalArgumentException("Encrypted Kakao token is invalid");
        }
        byte[] iv = new byte[IV_LENGTH];
        byte[] ciphertext = new byte[value.length - IV_LENGTH];
        ByteBuffer.wrap(value).get(iv).get(ciphertext);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Failed to decrypt Kakao token", exception);
        }
    }
}
