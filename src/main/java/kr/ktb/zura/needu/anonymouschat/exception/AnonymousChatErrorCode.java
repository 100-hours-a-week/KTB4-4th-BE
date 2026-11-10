package kr.ktb.zura.needu.anonymouschat.exception;

import kr.ktb.zura.needu.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AnonymousChatErrorCode implements ErrorCode {

    ANONYMOUS_CHAT_ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "익명대화방을 찾을 수 없습니다."),
    ANONYMOUS_CHAT_ROOM_FORBIDDEN(HttpStatus.FORBIDDEN, "해당 익명대화에 접근할 수 없습니다."),
    ANONYMOUS_CHAT_ROOM_ENDED(HttpStatus.CONFLICT, "종료된 익명대화입니다."),
    ANONYMOUS_CHAT_ROOM_EXPIRED(HttpStatus.CONFLICT, "만료된 익명대화입니다."),
    ANONYMOUS_CHAT_PARTICIPANT_LEFT(HttpStatus.FORBIDDEN, "이미 나간 익명대화입니다."),
    ANONYMOUS_CHAT_PARTNER_LEFT(HttpStatus.CONFLICT, "상대방이 나간 익명대화입니다."),
    ANONYMOUS_CHAT_REPORT_DUPLICATED(HttpStatus.CONFLICT, "이미 신고한 익명대화입니다."),
    ANONYMOUS_CHAT_ALIAS_EXHAUSTED(HttpStatus.CONFLICT, "사용 가능한 익명대화 상대명이 없습니다.");

    private final HttpStatus status;
    private final String message;
}
