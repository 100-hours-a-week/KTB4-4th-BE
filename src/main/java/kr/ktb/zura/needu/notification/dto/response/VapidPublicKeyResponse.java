package kr.ktb.zura.needu.notification.dto.response;

public record VapidPublicKeyResponse(String publicKey) {

    public static VapidPublicKeyResponse from(String publicKey) {
        return new VapidPublicKeyResponse(publicKey);
    }
}
