package kr.ktb.zura.needu.aichat.client;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import kr.ktb.zura.needu.aichat.exception.AiChatErrorCode;
import kr.ktb.zura.needu.common.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiServerEventStreamReaderTest {

    private static final String FIRST_DELTA = """
            event: delta
            data: {"index": 0, "text": "캠핑 "}

            """;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final List<AiServerEventStreamReader> readers = new ArrayList<>();
    private final List<String> deltas = new ArrayList<>();

    @AfterEach
    void tearDown() {
        readers.forEach(AiServerEventStreamReader::close);
    }

    @Test
    void noEventWithinIdleTimeout_read_closesStreamAndThrowsRequestTimeout() {
        AiServerEventStreamReader reader = reader(Duration.ofMillis(100), Duration.ofSeconds(10));
        StallingInputStream body = new StallingInputStream(FIRST_DELTA);

        assertThatThrownBy(() -> reader.read(body, deltas::add))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(AiChatErrorCode.AICHAT_REQUEST_TIMEOUT);
        assertThat(deltas).containsExactly("캠핑 ");
        assertThat(body.isClosed()).isTrue();
    }

    @Test
    void pingsKeepArrivingPastTotalTimeout_read_throwsRequestTimeout() {
        AiServerEventStreamReader reader = reader(Duration.ofSeconds(10), Duration.ofMillis(150));
        PingingInputStream body = new PingingInputStream(Duration.ofMillis(20));

        assertThatThrownBy(() -> reader.read(body, deltas::add))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(AiChatErrorCode.AICHAT_REQUEST_TIMEOUT);
    }

    @Test
    void completedWithoutDeltas_read_returnsResponseWithoutCallingOnDelta() {
        AiServerEventStreamReader reader = reader(Duration.ofSeconds(10), Duration.ofSeconds(10));
        InputStream body = new ByteArrayInputStream("""
                event: completed
                data: {"reply": "캠핑 좋죠.", "inputLocked": false, "progress": 15}

                """.getBytes(StandardCharsets.UTF_8));

        var response = reader.read(body, deltas::add);

        assertThat(response.reply()).isEqualTo("캠핑 좋죠.");
        assertThat(deltas).isEmpty();
    }

    @Test
    void nonPositiveTimeout_create_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> new AiServerEventStreamReader(jsonMapper, Duration.ZERO, Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private AiServerEventStreamReader reader(Duration idleTimeout, Duration totalTimeout) {
        AiServerEventStreamReader reader = new AiServerEventStreamReader(jsonMapper, idleTimeout, totalTimeout);
        readers.add(reader);
        return reader;
    }

    // 처음 내용을 보낸 뒤 닫힐 때까지 아무것도 보내지 않는 AI 응답
    private static final class StallingInputStream extends InputStream {

        private final byte[] initial;
        private final CountDownLatch closed = new CountDownLatch(1);
        private int position;

        private StallingInputStream(String initial) {
            this.initial = initial.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public int read() throws IOException {
            if (position < initial.length) {
                return initial[position++] & 0xFF;
            }
            try {
                if (closed.await(5, TimeUnit.SECONDS)) {
                    throw new IOException("closed");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            return -1;
        }

        // 기본 구현은 버퍼를 다 채울 때까지 기다리므로, 실제 소켓처럼 받은 만큼만 돌려준다
        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (position < initial.length) {
                int count = Math.min(length, initial.length - position);
                System.arraycopy(initial, position, buffer, offset, count);
                position += count;
                return count;
            }
            int value = read();
            if (value < 0) {
                return -1;
            }
            buffer[offset] = (byte) value;
            return 1;
        }

        @Override
        public void close() {
            closed.countDown();
        }

        private boolean isClosed() {
            return closed.getCount() == 0;
        }
    }

    // 닫힐 때까지 일정 간격으로 주석(: ping)만 보내는 AI 응답
    private static final class PingingInputStream extends InputStream {

        private static final byte[] PING = ": ping\n".getBytes(StandardCharsets.UTF_8);

        private final Duration interval;
        private volatile boolean closed;
        private int position = PING.length;

        private PingingInputStream(Duration interval) {
            this.interval = interval;
        }

        @Override
        public int read() throws IOException {
            if (closed) {
                throw new IOException("closed");
            }
            if (position == PING.length) {
                try {
                    Thread.sleep(interval);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
                position = 0;
            }
            return PING[position++] & 0xFF;
        }

        // 실제 소켓처럼 주석 한 줄씩 받은 만큼만 돌려준다
        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int count = 0;
            do {
                buffer[offset + count++] = (byte) read();
            } while (count < length && position < PING.length);
            return count;
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
