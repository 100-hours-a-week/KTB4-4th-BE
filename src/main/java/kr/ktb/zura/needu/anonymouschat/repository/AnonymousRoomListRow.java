package kr.ktb.zura.needu.anonymouschat.repository;

import java.time.LocalDateTime;

public record AnonymousRoomListRow(
        Long roomId,
        Long productId,
        Long recipientUserId,
        String questionerAlias,
        String questionerAvatarAnimal,
        String questionerAvatarColor,
        LocalDateTime lastActivityAt
) {
}
