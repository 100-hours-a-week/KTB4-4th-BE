package kr.ktb.zura.needu.common.response;

import kr.ktb.zura.needu.common.exception.ErrorCode;

public record ApiResponse<T>(String message, T data) {

    public static <T> ApiResponse<T> of(String message, T data) {
        return new ApiResponse<>(message, data);
    }

    public static ApiResponse<Void> from(ErrorCode errorCode) {
        return new ApiResponse<>(errorCode.getMessage(), null);
    }
}
