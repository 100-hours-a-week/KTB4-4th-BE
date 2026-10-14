package kr.ktb.zura.needu.aichat.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import kr.ktb.zura.needu.aichat.client.dto.response.AiServerErrorResponse;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerMessageDeltaResponse;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerSendMessageResponse;
import kr.ktb.zura.needu.aichat.exception.AiChatErrorCode;
import kr.ktb.zura.needu.common.exception.BusinessException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

final class AiServerEventStreamReader implements AutoCloseable {

    private static final String DELTA_EVENT = "delta";
    private static final String COMPLETED_EVENT = "completed";
    private static final String ERROR_EVENT = "error";
    private static final String SESSION_CLOSED_CODE = "SESSION_CLOSED";
    private static final Duration MIN_CHECK_INTERVAL = Duration.ofMillis(10);
    private static final Duration MAX_CHECK_INTERVAL = Duration.ofSeconds(1);

    private final JsonMapper jsonMapper;
    private final Duration idleTimeout;
    private final Duration totalTimeout;
    private final Duration checkInterval;
    private final ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().name("aichat-stream-watchdog").daemon().factory());

    AiServerEventStreamReader(JsonMapper jsonMapper, Duration idleTimeout, Duration totalTimeout) {
        if (!idleTimeout.isPositive() || !totalTimeout.isPositive()) {
            throw new IllegalArgumentException("AI stream timeouts must be positive");
        }
        this.jsonMapper = jsonMapper;
        this.idleTimeout = idleTimeout;
        this.totalTimeout = totalTimeout;
        Duration interval = idleTimeout.dividedBy(5);
        this.checkInterval = interval.compareTo(MIN_CHECK_INTERVAL) < 0 ? MIN_CHECK_INTERVAL
                : interval.compareTo(MAX_CHECK_INTERVAL) > 0 ? MAX_CHECK_INTERVAL : interval;
    }

    AiServerSendMessageResponse read(InputStream body, Consumer<String> onDelta) {
        StreamTimer timer = new StreamTimer(body);
        ScheduledFuture<?> check = watchdog.scheduleAtFixedRate(
                timer::closeIfTimedOut, checkInterval.toMillis(), checkInterval.toMillis(), TimeUnit.MILLISECONDS);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
            String eventName = null;
            StringBuilder data = new StringBuilder();
            int nextDeltaIndex = 0;
            String line;
            while ((line = reader.readLine()) != null) {
                timer.markActivity();
                if (line.isEmpty()) {
                    if (!data.isEmpty()) {
                        String type = eventName == null ? "" : eventName;
                        switch (type) {
                            case DELTA_EVENT -> {
                                AiServerMessageDeltaResponse delta = parse(data, AiServerMessageDeltaResponse.class);
                                if (delta.index() == null || delta.index() != nextDeltaIndex) {
                                    throw new BusinessException(AiChatErrorCode.AICHAT_INVALID_RESPONSE);
                                }
                                nextDeltaIndex++;
                                if (delta.text() != null && !delta.text().isEmpty()) {
                                    onDelta.accept(delta.text());
                                }
                            }
                            case COMPLETED_EVENT -> {
                                return parse(data, AiServerSendMessageResponse.class);
                            }
                            case ERROR_EVENT -> throw new BusinessException(
                                    toErrorCode(parse(data, AiServerErrorResponse.class)));
                            default -> {
                            }
                        }
                    }
                    eventName = null;
                    data.setLength(0);
                    continue;
                }
                if (line.startsWith(":")) {
                    continue;
                }
                int colon = line.indexOf(':');
                String field = colon < 0 ? line : line.substring(0, colon);
                String value = colon < 0 ? "" : line.substring(colon + 1);
                if (value.startsWith(" ")) {
                    value = value.substring(1);
                }
                if ("event".equals(field)) {
                    eventName = value;
                } else if ("data".equals(field)) {
                    if (!data.isEmpty()) {
                        data.append('\n');
                    }
                    data.append(value);
                }
            }

            throw new BusinessException(timer.isTimedOut()
                    ? AiChatErrorCode.AICHAT_REQUEST_TIMEOUT
                    : AiChatErrorCode.AICHAT_SERVER_UNAVAILABLE);
        } catch (IOException exception) {
            throw new BusinessException(timer.isTimedOut()
                    ? AiChatErrorCode.AICHAT_REQUEST_TIMEOUT
                    : AiChatErrorCode.AICHAT_SERVER_UNAVAILABLE);
        } finally {
            check.cancel(false);
        }
    }

    @Override
    public void close() {
        watchdog.shutdownNow();
    }

    private <T> T parse(CharSequence data, Class<T> type) {
        try {
            T value = jsonMapper.readValue(data.toString(), type);
            if (value == null) {
                throw new BusinessException(AiChatErrorCode.AICHAT_INVALID_RESPONSE);
            }
            return value;
        } catch (JacksonException exception) {
            throw new BusinessException(AiChatErrorCode.AICHAT_INVALID_RESPONSE);
        }
    }

    private AiChatErrorCode toErrorCode(AiServerErrorResponse error) {
        if (SESSION_CLOSED_CODE.equals(error.code())) {
            return AiChatErrorCode.AICHAT_SESSION_CLOSED;
        }
        return Boolean.FALSE.equals(error.retryable())
                ? AiChatErrorCode.AICHAT_REQUEST_REJECTED
                : AiChatErrorCode.AICHAT_SERVER_UNAVAILABLE;
    }

    private final class StreamTimer {

        private final InputStream body;
        private final long startedAt = System.nanoTime();
        private volatile long lastActivityAt = startedAt;
        private volatile boolean timedOut;

        private StreamTimer(InputStream body) {
            this.body = body;
        }

        private void markActivity() {
            lastActivityAt = System.nanoTime();
        }

        private boolean isTimedOut() {
            return timedOut;
        }

        private void closeIfTimedOut() {
            long now = System.nanoTime();
            if (timedOut
                    || (now - lastActivityAt < idleTimeout.toNanos() && now - startedAt < totalTimeout.toNanos())) {
                return;
            }
            timedOut = true;
            try {
                body.close();
            } catch (IOException ignored) {
            }
        }
    }
}
