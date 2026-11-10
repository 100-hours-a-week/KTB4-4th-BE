package kr.ktb.zura.needu.anonymouschat.support;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

public final class AnonymousChatTime {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private AnonymousChatTime() {
    }

    public static OffsetDateTime toKst(LocalDateTime utcDateTime) {
        return utcDateTime.atOffset(ZoneOffset.UTC)
                .atZoneSameInstant(KOREA_ZONE)
                .toOffsetDateTime();
    }
}
