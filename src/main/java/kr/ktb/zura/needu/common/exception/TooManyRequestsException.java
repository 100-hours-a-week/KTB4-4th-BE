package kr.ktb.zura.needu.common.exception;

import kr.ktb.zura.needu.common.response.RetryAfterResponse;

public class TooManyRequestsException extends BusinessException {

    public TooManyRequestsException(long retryAfterSeconds) {
        super(CommonErrorCode.COMMON_TOO_MANY_REQUESTS, new RetryAfterResponse(retryAfterSeconds));
    }
}
