package kr.ktb.zura.needu.aichat.repository;

import java.time.Duration;

// oldestWaiting: 아직 어느 워커도 가져가지 않은 항목 중 가장 오래된 것이 기다린 시간. 없으면 0
public record AiReplyStreamStatus(long length, long pendingCount, Duration oldestWaiting) {
}
