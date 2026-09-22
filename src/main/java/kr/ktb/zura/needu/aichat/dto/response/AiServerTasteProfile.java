package kr.ktb.zura.needu.aichat.dto.response;

import java.util.List;

public record AiServerTasteProfile(
        String schemaVersion,
        Long userId,
        String summary,
        List<AiServerProfileItem> interests,
        List<AiServerProfileItem> hobbies,
        List<AiServerProfileItem> preferences,
        List<AiServerProfileItem> lifestyle,
        List<AiServerProfileItem> wants,
        List<AiServerProfileItem> unaffordable,
        List<AiServerProfileItem> consumables,
        List<AiServerProfileItem> owned,
        List<AiServerProfileItem> dislikes,
        List<AiServerProfileItem> constraints,
        List<String> axes
) {
}
