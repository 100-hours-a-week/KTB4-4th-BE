package kr.ktb.zura.needu.anonymouschat.type;

public enum AnonymousRoomEndedReason {
    ME_LEFT,
    PARTNER_LEFT;

    public static AnonymousRoomEndedReason of(Long endedByUserId, Long viewerUserId) {
        return endedByUserId.equals(viewerUserId) ? ME_LEFT : PARTNER_LEFT;
    }
}
