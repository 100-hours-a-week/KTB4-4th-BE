package kr.ktb.zura.needu.aichat.facade;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import kr.ktb.zura.needu.aichat.config.AiReplyConsumerName;
import kr.ktb.zura.needu.aichat.config.AiReplyQueueProperties;
import kr.ktb.zura.needu.aichat.repository.AiReplyHeartbeatResult;
import kr.ktb.zura.needu.aichat.repository.AiReplyStreamEntry;
import kr.ktb.zura.needu.aichat.service.AiConversationLock;
import kr.ktb.zura.needu.aichat.service.AiGenerationStateService;
import kr.ktb.zura.needu.aichat.service.AiReplyQueueService;
import kr.ktb.zura.needu.aichat.service.AiReplyTaskExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

// 작업 실행기보다 늦게 시작하고 먼저 멈춘다
@Slf4j
@Component
@ConditionalOnProperty(name = "ai.chat.reply-queue.worker-enabled", havingValue = "true")
public class AiReplyWorker implements SmartLifecycle {

    private static final int PHASE = AiReplyTaskExecutor.PHASE + 1;
    private static final Duration RETURN_GRACE = Duration.ofSeconds(5);
    private static final Duration SHUTDOWN_CHECK_INTERVAL = Duration.ofMillis(100);

    private final AiChatFacade aiChatFacade;
    private final AiReplyQueueService aiReplyQueueService;
    private final AiReplyTaskExecutor aiReplyTaskExecutor;
    private final AiConversationLock aiConversationLock;
    private final AiGenerationStateService aiGenerationStateService;
    private final AiReplyQueueProperties properties;
    private final String consumerName;
    private final Clock clock;
    private final Duration shutdownTimeout;
    private final Set<AiReplyLease> activeLeases = ConcurrentHashMap.newKeySet();
    private volatile boolean running;
    private volatile Thread reader;
    private Instant nextClaimAt;

    public AiReplyWorker(AiChatFacade aiChatFacade,
                         AiReplyQueueService aiReplyQueueService,
                         AiReplyTaskExecutor aiReplyTaskExecutor,
                         AiConversationLock aiConversationLock,
                         AiGenerationStateService aiGenerationStateService,
                         AiReplyQueueProperties properties,
                         AiReplyConsumerName consumerName,
                         Clock clock,
                         @Value("${ai.chat.reply-shutdown-timeout}") Duration shutdownTimeout) {
        this.aiChatFacade = aiChatFacade;
        this.aiReplyQueueService = aiReplyQueueService;
        this.aiReplyTaskExecutor = aiReplyTaskExecutor;
        this.aiConversationLock = aiConversationLock;
        this.aiGenerationStateService = aiGenerationStateService;
        this.properties = properties;
        this.consumerName = consumerName.value();
        this.clock = clock;
        this.shutdownTimeout = shutdownTimeout;
        this.nextClaimAt = clock.instant();
    }

    @Override
    public void start() {
        aiReplyQueueService.createGroupIfAbsent();
        running = true;
        reader = Thread.ofVirtual().name("aichat-reply-reader").start(this::readLoop);
        log.info("AI reply worker started. consumer={}", consumerName);
    }

    @Override
    public void stop() {
        running = false;
        stopReader();
        awaitActiveJobs();
    }

    private void stopReader() {
        Thread current = reader;
        if (current == null) {
            return;
        }
        current.interrupt();
        try {
            current.join(properties.pollInterval().multipliedBy(5));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // 실행기의 종료 대기가 끝나면 남은 작업이 실패로 닫힘 => 그 전에 끊어서 다른 인스턴스가 이어 가게
    private void awaitActiveJobs() {
        if (awaitIdle(shutdownTimeout)) {
            return;
        }
        log.warn("AI reply jobs returning to queue on shutdown. consumer={}, remaining={}",
                consumerName, activeLeases.size());
        activeLeases.forEach(AiReplyLease::requestReturn);
        awaitIdle(RETURN_GRACE);
    }

    private boolean awaitIdle(Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (!activeLeases.isEmpty()) {
            if (System.nanoTime() - deadline >= 0) {
                return false;
            }
            try {
                Thread.sleep(SHUTDOWN_CHECK_INTERVAL);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return activeLeases.isEmpty();
            }
        }
        return true;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return PHASE;
    }

    private void readLoop() {
        while (running) {
            try {
                if (!dispatchNext()) {
                    Thread.sleep(properties.pollInterval());
                }
            } catch (InterruptedException e) {
                return;
            } catch (RuntimeException e) {
                log.warn("AI reply worker read failed. consumer={}", consumerName, e);
                if (!sleepQuietly()) {
                    return;
                }
            }
        }
    }

    boolean dispatchNext() {
        Optional<AiReplyTaskExecutor.Slot> slot = aiReplyTaskExecutor.tryReserve();
        if (slot.isEmpty()) {
            return false;
        }
        String permitId = UUID.randomUUID().toString();
        boolean dispatched = false;
        try {
            if (!aiReplyQueueService.tryAcquirePermit(permitId)) {
                return false;
            }
            Optional<AiReplyStreamEntry> entry = findNextEntry();
            if (entry.isEmpty()) {
                return false;
            }
            execute(slot.get(), entry.get(), permitId);
            dispatched = true;
            return true;
        } finally {
            if (!dispatched) {
                releasePermit(permitId);
                slot.get().release();
            }
        }
    }

    private Optional<AiReplyStreamEntry> findNextEntry() {
        Instant now = clock.instant();
        if (!now.isBefore(nextClaimAt)) {
            nextClaimAt = now.plus(properties.claimInterval());
            Optional<AiReplyStreamEntry> claimed = aiReplyQueueService.claimIdle(consumerName);
            if (claimed.isPresent()) {
                return claimed;
            }
        }
        return aiReplyQueueService.readNew(consumerName);
    }

    // 종료할 때 아직 시작 전인 작업도 기다릴 수 있도록 실행 전에 등록
    private void execute(AiReplyTaskExecutor.Slot slot, AiReplyStreamEntry entry, String permitId) {
        AiReplyLease lease = AiReplyLease.start(clock);
        activeLeases.add(lease);
        try {
            slot.execute(() -> process(entry, permitId, lease));
        } catch (RuntimeException e) {
            activeLeases.remove(lease);
            throw e;
        }
    }

    void process(AiReplyStreamEntry entry, String permitId, AiReplyLease lease) {
        lease.bindThread(Thread.currentThread());
        try {
            if (entry.isMalformed()) {
                log.warn("AI reply stream entry malformed. streamEntryId={}", entry.entryId());
                acknowledge(entry);
                return;
            }
            processJob(entry, permitId, lease);
        } finally {
            releasePermit(permitId);
            activeLeases.remove(lease);
        }
    }

    private void processJob(AiReplyStreamEntry entry, String permitId, AiReplyLease lease) {
        Thread heartbeat = Thread.ofVirtual()
                .name("aichat-reply-heartbeat-" + entry.entryId())
                .start(() -> keepAlive(entry, permitId, lease));
        try {
            AiReplyJobOutcome outcome = aiChatFacade.replyToJob(
                    entry.jobId(), entry.dispatchToken(), entry.deliveryCount(), entry.entryId(), lease);
            if (outcome == AiReplyJobOutcome.FINISHED || outcome == AiReplyJobOutcome.RETURNED) {
                acknowledge(entry);
            }
        } catch (RuntimeException e) {
            // ACK하지 않음 => 하트비트가 멈춘 뒤 다른 워커가 회수해 다시 처리
            log.error("AI reply job processing failed. jobId={}, streamEntryId={}", entry.jobId(), entry.entryId(), e);
        } finally {
            heartbeat.interrupt();
        }
    }

    private void keepAlive(AiReplyStreamEntry entry, String permitId, AiReplyLease lease) {
        while (!lease.isLost()) {
            try {
                Thread.sleep(properties.heartbeatInterval());
            } catch (InterruptedException e) {
                return;
            }
            extendLease(entry, permitId, lease);
        }
    }

    void extendLease(AiReplyStreamEntry entry, String permitId, AiReplyLease lease) {
        AiReplyHeartbeatResult result;
        try {
            result = aiReplyQueueService.heartbeat(consumerName, entry.entryId());
        } catch (RuntimeException e) {
            // 결과를 모른다 => 다음 주기에 다시 보낸다. 회수 기준 시간이 지나면 이미 회수됐을 수 있어 스스로 멈춘다
            if (!lease.isConfirmedWithin(properties.claimMinIdle())) {
                lease.markLost();
                log.warn("AI reply job lease expired. jobId={}, streamEntryId={}", entry.jobId(), entry.entryId(), e);
                return;
            }
            log.warn("AI reply heartbeat failed. jobId={}, streamEntryId={}", entry.jobId(), entry.entryId(), e);
            return;
        }
        if (result != AiReplyHeartbeatResult.OWNED) {
            lease.markLost();
            log.info("AI reply job lease lost. jobId={}, streamEntryId={}, result={}",
                    entry.jobId(), entry.entryId(), result);
            return;
        }
        lease.confirm();
        extendHolds(entry, permitId, lease);
    }

    // 하나가 실패해도 다음 하트비트에서 다시 연장. 중복 저장은 DB의 처리 번호가 막는다
    private void extendHolds(AiReplyStreamEntry entry, String permitId, AiReplyLease lease) {
        try {
            aiReplyQueueService.extendPermit(permitId);
            lease.target().ifPresent(target -> {
                aiConversationLock.ensure(new AiConversationLock.Handle(target.conversationId(), target.lockToken()));
                aiGenerationStateService.extendGenerating(target.conversationId(), target.userMessageId());
            });
        } catch (RuntimeException e) {
            log.warn("AI reply lease extension failed. jobId={}, streamEntryId={}", entry.jobId(), entry.entryId(), e);
        }
    }

    private void acknowledge(AiReplyStreamEntry entry) {
        try {
            aiReplyQueueService.acknowledge(entry.entryId());
        } catch (RuntimeException e) {
            log.warn("AI reply stream entry acknowledge failed. jobId={}, streamEntryId={}",
                    entry.jobId(), entry.entryId(), e);
        }
    }

    private void releasePermit(String permitId) {
        try {
            aiReplyQueueService.releasePermit(permitId);
        } catch (RuntimeException e) {
            log.warn("AI reply permit release failed. permitId={}", permitId, e);
        }
    }

    private boolean sleepQuietly() {
        try {
            Thread.sleep(properties.pollInterval());
            return true;
        } catch (InterruptedException e) {
            return false;
        }
    }
}
