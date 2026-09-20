package kr.ktb.zura.needu.friend.exception;

import kr.ktb.zura.needu.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum FriendErrorCode implements ErrorCode {

    FRIEND_NOT_FOUND(HttpStatus.NOT_FOUND, "친구를 찾을 수 없습니다."),
    FRIEND_KAKAO_INVALID_STATE(HttpStatus.BAD_REQUEST, "카카오 친구 연동 요청이 유효하지 않습니다."),
    FRIEND_KAKAO_INVALID_RETURN_URL(HttpStatus.BAD_REQUEST, "돌아갈 주소가 유효하지 않습니다."),
    FRIEND_KAKAO_SYNC_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "카카오 친구 목록을 불러오지 못했습니다.");

    private final HttpStatus status;
    private final String message;
}
