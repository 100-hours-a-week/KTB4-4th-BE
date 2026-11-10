package kr.ktb.zura.needu.anonymouschat.service;

import java.time.DateTimeException;
import java.time.LocalDateTime;
import kr.ktb.zura.needu.anonymouschat.repository.AnonymousRoomListRow;
import kr.ktb.zura.needu.common.cursor.CursorCodec;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.CommonErrorCode;

public record AnonymousRoomCursor(LocalDateTime lastActivityAt, Long roomId) {

    private static final String DELIMITER = "|";
    private static final int PART_COUNT = 2;

    public static AnonymousRoomCursor from(AnonymousRoomListRow room) {
        return new AnonymousRoomCursor(room.lastActivityAt(), room.roomId());
    }

    public static AnonymousRoomCursor decode(String cursor) {
        try {
            String[] parts = CursorCodec.decode(cursor).split("\\|", -1);
            if (parts.length != PART_COUNT) {
                throw invalidCursor();
            }
            LocalDateTime lastActivityAt = LocalDateTime.parse(parts[0]);
            long roomId = Long.parseLong(parts[1]);
            if (roomId <= 0) {
                throw invalidCursor();
            }
            return new AnonymousRoomCursor(lastActivityAt, roomId);
        } catch (DateTimeException | IllegalArgumentException exception) {
            throw invalidCursor();
        }
    }

    public String encode() {
        return CursorCodec.encode(lastActivityAt + DELIMITER + roomId);
    }

    private static BusinessException invalidCursor() {
        return new BusinessException(CommonErrorCode.COMMON_INVALID_REQUEST);
    }
}
