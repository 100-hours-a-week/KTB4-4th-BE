package kr.ktb.zura.needu.aichat.repository;

import java.util.List;

public record AiReplyStreamClaimResult(
        AiReplyStreamEntry claimed,
        List<String> deletedEntryIds
) {

    public boolean hasClaimed() {
        return claimed != null;
    }
}
