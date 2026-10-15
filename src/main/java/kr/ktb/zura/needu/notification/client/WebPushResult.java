package kr.ktb.zura.needu.notification.client;

public enum WebPushResult {
    SUCCESS,
    EXPIRED,
    FAILED;

    public static WebPushResult fromStatusCode(int statusCode) {
        if (statusCode == 201) {
            return SUCCESS;
        }
        if (statusCode == 404 || statusCode == 410) {
            return EXPIRED;
        }
        return FAILED;
    }
}
