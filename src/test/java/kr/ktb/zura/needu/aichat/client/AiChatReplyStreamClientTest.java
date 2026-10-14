package kr.ktb.zura.needu.aichat.client;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import kr.ktb.zura.needu.aichat.client.dto.response.AiServerSendMessageResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class AiChatReplyStreamClientTest {

    private static final Long USER_ID = 1L;
    private static final Long ROOM_ID = 101L;
    private static final String MESSAGE = "요즘 러닝에 관심이 생겼어.";

    private final AiChatClient aiChatClient = mock(AiChatClient.class);
    private final List<String> chunks = new ArrayList<>();

    @Test
    void longReply_streamMessage_passesChunksInOrderAndReturnsResponse() {
        AiServerSendMessageResponse response = givenReply("가".repeat(20) + "나".repeat(20) + "다".repeat(5));
        AiChatReplyStreamClient client = new AiChatReplyStreamClient(aiChatClient, 20, Duration.ZERO);

        AiServerSendMessageResponse result = client.streamMessage(USER_ID, ROOM_ID, MESSAGE, chunks::add);

        assertThat(chunks).containsExactly("가".repeat(20), "나".repeat(20), "다".repeat(5));
        assertThat(result).isSameAs(response);
    }

    @Test
    void emojiAtChunkBoundary_streamMessage_keepsEmojiInOneChunk() {
        givenReply("A".repeat(19) + "😀" + "B");
        AiChatReplyStreamClient client = new AiChatReplyStreamClient(aiChatClient, 20, Duration.ZERO);

        client.streamMessage(USER_ID, ROOM_ID, MESSAGE, chunks::add);

        assertThat(chunks).containsExactly("A".repeat(19) + "😀", "B");
    }

    @Test
    void chunkInterval_streamMessage_waitsBetweenChunks() {
        givenReply("abc");
        AiChatReplyStreamClient client = new AiChatReplyStreamClient(aiChatClient, 1, Duration.ofMillis(30));

        long startedAt = System.nanoTime();
        client.streamMessage(USER_ID, ROOM_ID, MESSAGE, chunks::add);
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);

        assertThat(chunks).containsExactly("a", "b", "c");
        assertThat(elapsed).isGreaterThanOrEqualTo(Duration.ofMillis(60));
    }

    @Test
    void replyMissing_streamMessage_returnsResponseWithoutChunks() {
        AiServerSendMessageResponse response = givenReply(null);
        AiChatReplyStreamClient client = new AiChatReplyStreamClient(aiChatClient, 20, Duration.ZERO);

        AiServerSendMessageResponse result = client.streamMessage(USER_ID, ROOM_ID, MESSAGE, chunks::add);

        assertThat(chunks).isEmpty();
        assertThat(result).isSameAs(response);
    }

    @Test
    void invalidChunkSettings_create_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> new AiChatReplyStreamClient(aiChatClient, 0, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AiChatReplyStreamClient(aiChatClient, 20, Duration.ofMillis(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private AiServerSendMessageResponse givenReply(String reply) {
        AiServerSendMessageResponse response = new AiServerSendMessageResponse(
                reply, OffsetDateTime.now(), OffsetDateTime.now().plusMinutes(30), 1, 10, false, false, 10);
        given(aiChatClient.sendMessage(USER_ID, ROOM_ID, MESSAGE)).willReturn(response);
        return response;
    }
}
