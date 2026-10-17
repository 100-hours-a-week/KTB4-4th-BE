package kr.ktb.zura.needu.auth.type;

public enum KakaoOAuthFlow {
    LOGIN("login"),
    FRIEND("friend");

    private final String key;

    KakaoOAuthFlow(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}
