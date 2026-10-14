package kr.ktb.zura.needu.aichat.service;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
class AiChatStreamConnection {

    private final Long conversationId;
    private final SseEmitter emitter;
    private final Executor sendExecutor;
    private final Consumer<AiChatStreamConnection> onClosed;
    private final BlockingQueue<SseEmitter.SseEventBuilder> pendingEvents;

    private final AtomicBoolean sending = new AtomicBoolean(true);
    private final AtomicBoolean closed = new AtomicBoolean();

    AiChatStreamConnection(
            Long conversationId,
            SseEmitter emitter,
            Executor sendExecutor,
            int maxPendingEvents,
            Consumer<AiChatStreamConnection> onClosed
    ) {
        this.conversationId = conversationId;
        this.emitter = emitter;
        this.sendExecutor = sendExecutor;
        this.onClosed = onClosed;
        this.pendingEvents = new LinkedBlockingQueue<>(maxPendingEvents);
    }

    void open(SseEmitter.SseEventBuilder connectedEvent) throws IOException {
        emitter.send(connectedEvent);
        releaseSending();
    }

    void enqueue(SseEmitter.SseEventBuilder event) {
        if (closed.get()) {
            return;
        }
        if (!pendingEvents.offer(event)) {
            log.warn("AI chat stream closed by pending event limit. conversationId={}", conversationId);
            close();
            sendExecutor.execute(emitter::complete);
            return;
        }
        if (sending.compareAndSet(false, true)) {
            sendExecutor.execute(this::sendPendingEvents);
        }
    }

    void close() {
        if (closed.compareAndSet(false, true)) {
            pendingEvents.clear();
            onClosed.accept(this);
        }
    }

    private void sendPendingEvents() {
        SseEmitter.SseEventBuilder event;
        while (!closed.get() && (event = pendingEvents.poll()) != null) {
            try {
                emitter.send(event);
            } catch (IOException | IllegalStateException exception) {
                close();
                emitter.completeWithError(exception);
                return;
            }
        }
        releaseSending();
    }

    private void releaseSending() {
        sending.set(false);
        if (!closed.get() && !pendingEvents.isEmpty() && sending.compareAndSet(false, true)) {
            sendExecutor.execute(this::sendPendingEvents);
        }
    }
}
