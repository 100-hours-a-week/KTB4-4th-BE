package kr.ktb.zura.needu.anonymouschat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import kr.ktb.zura.needu.anonymouschat.ws.AnonymousMessageRedisEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

class AnonymousReadPositionUpdatedEventListenerTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final AnonymousMessageEventPublisher publisher = mock(AnonymousMessageEventPublisher.class);
    private final AnonymousReadPositionUpdatedEventListener listener =
            new AnonymousReadPositionUpdatedEventListener(publisher, jsonMapper);

    @Test
    void readPositionUpdated_publish_sendsReadEventToPartner() {
        listener.publish(readEvent());

        ArgumentCaptor<AnonymousMessageRedisEvent> captor = ArgumentCaptor.forClass(AnonymousMessageRedisEvent.class);
        verify(publisher).publish(captor.capture());
        assertThat(captor.getValue().receiverUserId()).isEqualTo(200L);
        assertThat(captor.getValue().event().toString()).isEqualTo(jsonMapper.readTree("""
                {"type":"ANONYMOUS_CHAT_MESSAGE_READ",
                 "data":{"roomId":1,"lastReadMessageId":103,"readAt":"2026-10-28T10:05:00+09:00"}}
                """).toString());
    }

    @Test
    void publishFails_publish_doesNotThrow() {
        willThrow(new IllegalStateException("redis down")).given(publisher).publish(any());

        assertThatCode(() -> listener.publish(readEvent())).doesNotThrowAnyException();
    }

    private static AnonymousReadPositionUpdatedEvent readEvent() {
        return new AnonymousReadPositionUpdatedEvent(1L, 200L, 103L, LocalDateTime.of(2026, 10, 28, 1, 5));
    }
}
