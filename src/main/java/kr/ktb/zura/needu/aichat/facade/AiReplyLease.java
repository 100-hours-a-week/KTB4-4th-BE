package kr.ktb.zura.needu.aichat.facade;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

// 워커가 작업 하나를 처리하는 동안의 처리 권한
// ( 하트비트 스레드 => 갱신, 답변 생성 스레드 => 확인 )
public class AiReplyLease {

    private final Clock clock;
    private volatile Instant confirmedAt;
    private volatile boolean lost;
    private volatile Target target;

    private AiReplyLease(Clock clock) {
        this.clock = clock;
        this.confirmedAt = clock == null ? null : clock.instant();
    }

    public static AiReplyLease start(Clock clock) {
        return new AiReplyLease(clock);
    }

    // 지금 경로(접수한 서버가 바로 처리)
    public static AiReplyLease unmanaged() {
        return new AiReplyLease(null);
    }

    public boolean isManaged() {
        return clock != null;
    }

    public boolean isLost() {
        return lost;
    }

    public boolean isConfirmedWithin(Duration duration) {
        return !isManaged() || !confirmedAt.plus(duration).isBefore(clock.instant());
    }

    public Optional<Target> target() {
        return Optional.ofNullable(target);
    }

    void attach(Long conversationId, Long userMessageId, String lockToken) {
        this.target = new Target(conversationId, userMessageId, lockToken);
    }

    void confirm() {
        this.confirmedAt = clock.instant();
    }

    void markLost() {
        this.lost = true;
    }

    public record Target(Long conversationId, Long userMessageId, String lockToken) {
    }
}
