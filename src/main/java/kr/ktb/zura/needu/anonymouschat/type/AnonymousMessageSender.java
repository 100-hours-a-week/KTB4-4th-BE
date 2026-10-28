package kr.ktb.zura.needu.anonymouschat.type;

public enum AnonymousMessageSender {
    ME,
    OTHER;

    public static AnonymousMessageSender of(Long senderUserId, Long viewerUserId) {
        return senderUserId.equals(viewerUserId) ? ME : OTHER;
    }
}
