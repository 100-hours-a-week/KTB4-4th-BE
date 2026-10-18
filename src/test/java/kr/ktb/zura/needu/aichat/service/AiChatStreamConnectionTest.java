package kr.ktb.zura.needu.aichat.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class AiChatStreamConnectionTest {

    private static final Long CONVERSATION_ID = 101L;

    private final SseEmitter emitter = mock(SseEmitter.class);
    private final List<Runnable> scheduledTasks = new ArrayList<>();
    // 실제 쓰기 작업이 언제 실행되는지 테스트에서 정하기 위해 바로 실행하지 않고 모아 둔다
    private final Executor deferredExecutor = scheduledTasks::add;
    private final AtomicInteger closedCount = new AtomicInteger();

    private final SseEmitter.SseEventBuilder connected = SseEmitter.event().name("connected");
    private final SseEmitter.SseEventBuilder first = SseEmitter.event().name("delta").data("1");
    private final SseEmitter.SseEventBuilder second = SseEmitter.event().name("delta").data("2");
    private final SseEmitter.SseEventBuilder third = SseEmitter.event().name("delta").data("3");

    @Test
    void notOpened_enqueue_holdsEventsUntilConnectedIsSent() throws Exception {
        AiChatStreamConnection connection = connection(10);

        connection.enqueue(first);
        verify(emitter, never()).send(any(SseEmitter.SseEventBuilder.class));

        connection.open(connected);
        runScheduledTasks();

        InOrder order = inOrder(emitter);
        order.verify(emitter).send(connected);
        order.verify(emitter).send(first);
    }

    @Test
    void openedConnection_enqueue_returnsWithoutWritingAndSendsInOrderOnOneTask() throws Exception {
        AiChatStreamConnection connection = openedConnection(10);

        connection.enqueue(first);
        connection.enqueue(second);
        connection.enqueue(third);

        verify(emitter, never()).send(any(SseEmitter.SseEventBuilder.class));
        assertThat(scheduledTasks).hasSize(1);

        runScheduledTasks();

        InOrder order = inOrder(emitter);
        order.verify(emitter).send(first);
        order.verify(emitter).send(second);
        order.verify(emitter).send(third);
    }

    @Test
    void pendingEventsOverLimit_enqueue_closesConnection() throws Exception {
        AiChatStreamConnection connection = openedConnection(2);

        connection.enqueue(first);
        connection.enqueue(second);
        connection.enqueue(third);
        runScheduledTasks();

        verify(emitter).complete();
        verify(emitter, never()).send(any(SseEmitter.SseEventBuilder.class));
        assertThat(closedCount).hasValue(1);
    }

    @Test
    void pendingEventsOverLimit_enqueue_completesEmitterOutsideCallerThread() throws Exception {
        AiChatStreamConnection connection = openedConnection(1);

        connection.enqueue(first);
        connection.enqueue(second);

        verify(emitter, never()).complete();
        runScheduledTasks();
        verify(emitter).complete();
    }

    @Test
    void sendFails_enqueue_closesConnectionAndDropsRemainingEvents() throws Exception {
        AiChatStreamConnection connection = openedConnection(10);
        IOException disconnected = new IOException("disconnected");
        doThrow(disconnected).when(emitter).send(first);

        connection.enqueue(first);
        connection.enqueue(second);
        runScheduledTasks();
        connection.enqueue(third);
        runScheduledTasks();

        verify(emitter).completeWithError(disconnected);
        verify(emitter, times(1)).send(any(SseEmitter.SseEventBuilder.class));
        assertThat(closedCount).hasValue(1);
    }

    @Test
    void closedConnection_close_notifiesOnlyOnce() {
        AiChatStreamConnection connection = connection(10);

        connection.close();
        connection.close();

        assertThat(closedCount).hasValue(1);
    }

    private AiChatStreamConnection connection(int maxPendingEvents) {
        return new AiChatStreamConnection(CONVERSATION_ID, emitter, deferredExecutor, maxPendingEvents,
                closedConnection -> closedCount.incrementAndGet());
    }

    private AiChatStreamConnection openedConnection(int maxPendingEvents) throws IOException {
        AiChatStreamConnection connection = connection(maxPendingEvents);
        connection.open(connected);
        clearInvocations(emitter);
        return connection;
    }

    private void runScheduledTasks() {
        while (!scheduledTasks.isEmpty()) {
            scheduledTasks.removeFirst().run();
        }
    }
}
