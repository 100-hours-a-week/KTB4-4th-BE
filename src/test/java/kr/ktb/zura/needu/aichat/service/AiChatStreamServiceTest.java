package kr.ktb.zura.needu.aichat.service;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import kr.ktb.zura.needu.aichat.entity.AiChatRoom;
import kr.ktb.zura.needu.aichat.sse.AiChatConnectedEvent;
import kr.ktb.zura.needu.aichat.sse.AiChatEventType;
import kr.ktb.zura.needu.aichat.sse.AiChatRedisEvent;
import kr.ktb.zura.needu.aichat.type.AiChatRoomStatus;
import kr.ktb.zura.needu.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

class AiChatStreamServiceTest {

    private static final Long CONVERSATION_ID = 101L;
    private static final Long USER_MESSAGE_ID = 201L;
    // 이벤트 쓰기는 연결별 가상 스레드에서 실행되므로 검증할 때 잠시 기다린다
    private static final long SEND_WAIT_MILLIS = 1_000L;

    private final AiGenerationStateService aiGenerationStateService = mock(AiGenerationStateService.class);
    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final AiChatStreamService aiChatStreamService =
            new AiChatStreamService(aiGenerationStateService, Duration.ofMinutes(30), 1_000);

    @Test
    void activeRoom_connect_returnsEmitterWithConfiguredTimeout() {
        given(aiGenerationStateService.findGeneratingUserMessageId(CONVERSATION_ID)).willReturn(Optional.empty());

        SseEmitter emitter = aiChatStreamService.connect(activeRoom());

        assertThat(emitter.getTimeout()).isEqualTo(Duration.ofMinutes(30).toMillis());
    }

    @Test
    void replyBeingGenerated_connect_sendsConnectedWithPendingUserMessageId() throws Exception {
        given(aiGenerationStateService.findGeneratingUserMessageId(CONVERSATION_ID))
                .willReturn(Optional.of(USER_MESSAGE_ID));

        try (MockedConstruction<SseEmitter> emitters = mockConstruction(SseEmitter.class)) {
            aiChatStreamService.connect(activeRoom());

            Set<SseEmitter.DataWithMediaType> eventParts = sentEventParts(emitters.constructed().getFirst());
            assertThat(eventParts).extracting(part -> part.getData().toString())
                    .anyMatch(part -> part.contains("event:connected"));
            assertThat(eventParts).extracting(SseEmitter.DataWithMediaType::getData)
                    .contains(new AiChatConnectedEvent(
                            CONVERSATION_ID, AiChatRoomStatus.ACTIVE, false, true, USER_MESSAGE_ID));
        }
    }

    @Test
    void generationStateUnavailable_connect_throwsAndDoesNotKeepConnection() {
        given(aiGenerationStateService.findGeneratingUserMessageId(CONVERSATION_ID))
                .willThrow(new IllegalStateException("redis down"));

        assertThatThrownBy(() -> aiChatStreamService.connect(activeRoom()))
                .isInstanceOf(IllegalStateException.class);
        assertThat(connectionsByConversationId()).doesNotContainKey(CONVERSATION_ID);
    }

    @Test
    void connectedSendFails_connect_throwsServiceUnavailableAndDoesNotKeepConnection() throws Exception {
        given(aiGenerationStateService.findGeneratingUserMessageId(CONVERSATION_ID)).willReturn(Optional.empty());

        try (MockedConstruction<SseEmitter> ignored = mockConstruction(SseEmitter.class, (emitter, context) ->
                doThrow(new IOException("disconnected")).when(emitter).send(any(SseEmitter.SseEventBuilder.class)))) {
            assertThatThrownBy(() -> aiChatStreamService.connect(activeRoom()))
                    .isInstanceOf(BusinessException.class);
        }
        assertThat(connectionsByConversationId()).doesNotContainKey(CONVERSATION_ID);
    }

    @Test
    void connectedConversation_sendEvent_sendsNamedEventWithData() throws Exception {
        try (MockedConstruction<SseEmitter> emitters = mockConstruction(SseEmitter.class)) {
            SseEmitter emitter = connectEmitter(emitters);
            JsonNode data = jsonMapper.createObjectNode()
                    .put("userMessageId", USER_MESSAGE_ID)
                    .put("index", 0)
                    .put("text", "좋아요");

            aiChatStreamService.sendEvent(new AiChatRedisEvent(CONVERSATION_ID, AiChatEventType.DELTA, data));

            Set<SseEmitter.DataWithMediaType> eventParts = sentEventParts(emitter);
            assertThat(eventParts).extracting(part -> part.getData().toString())
                    .anyMatch(part -> part.contains("event:delta"));
            assertThat(eventParts).extracting(SseEmitter.DataWithMediaType::getData).contains(data);
        }
    }

    @Test
    void otherConversation_sendEvent_doesNotSend() throws Exception {
        try (MockedConstruction<SseEmitter> emitters = mockConstruction(SseEmitter.class)) {
            SseEmitter emitter = connectEmitter(emitters);

            aiChatStreamService.sendEvent(new AiChatRedisEvent(
                    999L, AiChatEventType.DONE, jsonMapper.createObjectNode()));

            verify(emitter, never()).send(any(SseEmitter.SseEventBuilder.class));
        }
    }

    @Test
    void emitterSendFails_sendEvent_removesFailedConnection() throws Exception {
        try (MockedConstruction<SseEmitter> emitters = mockConstruction(SseEmitter.class)) {
            SseEmitter emitter = connectEmitter(emitters);
            doThrow(new IOException("disconnected")).when(emitter).send(any(SseEmitter.SseEventBuilder.class));

            aiChatStreamService.sendEvent(new AiChatRedisEvent(
                    CONVERSATION_ID, AiChatEventType.DONE, jsonMapper.createObjectNode()));

            verify(emitter, timeout(SEND_WAIT_MILLIS)).completeWithError(any(IOException.class));
            assertThat(connectionsByConversationId()).doesNotContainKey(CONVERSATION_ID);
        }
    }

    @Test
    void oneConnectionNotReading_sendEvent_stillDeliversToOtherConnection() throws Exception {
        try (MockedConstruction<SseEmitter> emitters = mockConstruction(SseEmitter.class)) {
            SseEmitter stuckEmitter = connectEmitter(emitters);
            SseEmitter healthyEmitter = connectEmitter(emitters);
            CountDownLatch stuckRelease = new CountDownLatch(1);
            doAnswer(invocation -> {
                stuckRelease.await();
                return null;
            }).when(stuckEmitter).send(any(SseEmitter.SseEventBuilder.class));

            try {
                aiChatStreamService.sendEvent(new AiChatRedisEvent(
                        CONVERSATION_ID, AiChatEventType.DONE, jsonMapper.createObjectNode()));

                verify(healthyEmitter, timeout(SEND_WAIT_MILLIS)).send(any(SseEmitter.SseEventBuilder.class));
            } finally {
                stuckRelease.countDown();
            }
        }
    }

    @Test
    void connectedConversations_sendHeartbeat_sendsPingComment() throws Exception {
        try (MockedConstruction<SseEmitter> emitters = mockConstruction(SseEmitter.class)) {
            SseEmitter emitter = connectEmitter(emitters);

            aiChatStreamService.sendHeartbeat();

            assertThat(sentEventParts(emitter)).extracting(part -> part.getData().toString())
                    .anyMatch(part -> part.contains(":ping"));
        }
    }

    // connected 전송까지 끝낸 뒤 이후 전송만 검증할 수 있게 호출 기록을 비운다
    private SseEmitter connectEmitter(MockedConstruction<SseEmitter> emitters) {
        given(aiGenerationStateService.findGeneratingUserMessageId(CONVERSATION_ID)).willReturn(Optional.empty());
        aiChatStreamService.connect(activeRoom());
        SseEmitter emitter = emitters.constructed().getLast();
        clearInvocations(emitter);
        return emitter;
    }

    private Set<SseEmitter.DataWithMediaType> sentEventParts(SseEmitter emitter) throws IOException {
        ArgumentCaptor<SseEmitter.SseEventBuilder> eventCaptor =
                ArgumentCaptor.forClass(SseEmitter.SseEventBuilder.class);
        verify(emitter, timeout(SEND_WAIT_MILLIS)).send(eventCaptor.capture());
        return eventCaptor.getValue().build();
    }

    @SuppressWarnings("unchecked")
    private Map<Long, Set<AiChatStreamConnection>> connectionsByConversationId() {
        return (Map<Long, Set<AiChatStreamConnection>>) ReflectionTestUtils.getField(
                aiChatStreamService, "connectionsByConversationId");
    }

    private static AiChatRoom activeRoom() {
        AiChatRoom room = AiChatRoom.reserve(1L);
        room.activate(LocalDateTime.now().plusMinutes(30));
        // ID는 DB에서 결정되므로 단위 테스트에서만 직접 설정한다.
        ReflectionTestUtils.setField(room, "id", CONVERSATION_ID);
        return room;
    }
}
