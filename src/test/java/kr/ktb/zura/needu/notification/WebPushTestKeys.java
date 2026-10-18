package kr.ktb.zura.needu.notification;

import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;

public final class WebPushTestKeys {

    private static final int COORDINATE_LENGTH = 32;
    private static final int UNCOMPRESSED_POINT_LENGTH = 1 + COORDINATE_LENGTH * 2;
    private static final byte UNCOMPRESSED_POINT_PREFIX = 0x04;

    private WebPushTestKeys() {
    }

    public static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            return generator.generateKeyPair();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(exception);
        }
    }

    // 운영 설정과 같은 형식: 개인키는 PKCS8 Base64, 공개키는 uncompressed point Base64URL
    public static String toPrivateKeyProperty(KeyPair keyPair) {
        return Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
    }

    public static String toPublicKeyProperty(KeyPair keyPair) {
        return toBase64Url(toUncompressedBytes((ECPublicKey) keyPair.getPublic()));
    }

    public static byte[] toUncompressedBytes(ECPublicKey publicKey) {
        byte[] result = new byte[UNCOMPRESSED_POINT_LENGTH];
        result[0] = UNCOMPRESSED_POINT_PREFIX;
        copyCoordinate(publicKey.getW().getAffineX(), result, 1);
        copyCoordinate(publicKey.getW().getAffineY(), result, 1 + COORDINATE_LENGTH);
        return result;
    }

    public static String toBase64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static void copyCoordinate(BigInteger coordinate, byte[] target, int offset) {
        byte[] bytes = coordinate.toByteArray();
        int sourceOffset = Math.max(0, bytes.length - COORDINATE_LENGTH);
        int length = Math.min(bytes.length, COORDINATE_LENGTH);
        System.arraycopy(bytes, sourceOffset, target, offset + COORDINATE_LENGTH - length, length);
    }
}
