package kr.ktb.zura.needu.anonymouschat.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willAnswer;

import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import kr.ktb.zura.needu.anonymouschat.dto.request.SendAnonymousMessageRequest;
import kr.ktb.zura.needu.anonymouschat.entity.AnonymousMessage;
import kr.ktb.zura.needu.anonymouschat.entity.AnonymousRoom;
import kr.ktb.zura.needu.anonymouschat.repository.AnonymousMessageRepository;
import kr.ktb.zura.needu.anonymouschat.repository.AnonymousRoomRepository;
import kr.ktb.zura.needu.anonymouschat.service.AnonymousMessageEventPublisher;
import kr.ktb.zura.needu.anonymouschat.service.AnonymousMessageService;
import kr.ktb.zura.needu.anonymouschat.ws.AnonymousChatErrorEvent;
import kr.ktb.zura.needu.anonymouschat.ws.AnonymousChatEventSender;
import kr.ktb.zura.needu.anonymouschat.ws.AnonymousMessageRedisEvent;
import kr.ktb.zura.needu.auth.service.AccessTokenService;
import kr.ktb.zura.needu.common.exception.TooManyRequestsException;
import kr.ktb.zura.needu.common.security.AuthCookieNames;
import kr.ktb.zura.needu.user.entity.User;
import kr.ktb.zura.needu.user.repository.UserRepository;
import kr.ktb.zura.needu.user.type.Gender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.handler.annotation.support.DestinationVariableMethodArgumentResolver;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import tools.jackson.databind.JsonNode;

// Redis가 없는 테스트 환경이라 Pub/Sub 발행만 같은 서버의 세션 전송으로 바로 넘긴다.
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "needu.rate-limit.enabled=false"
)
class AnonymousChatMessageControllerTest {

    private static final String USER_QUEUE = "/user/queue/anonymous-chats";
    private static final long EVENT_TIMEOUT_SECONDS = 5;
    private static final long NO_EVENT_WAIT_MILLIS = 500;
    private static final int SUBSCRIBE_CHECK_ATTEMPTS = 20;
    private static final long SUBSCRIBE_CHECK_WAIT_MILLIS = 200;

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AnonymousRoomRepository anonymousRoomRepository;

    @Autowired
    private AnonymousMessageRepository anonymousMessageRepository;

    @Autowired
    private AccessTokenService accessTokenService;

    @Autowired
    private AnonymousChatEventSender eventSender;

    @Autowired
    private AnonymousChatMessageController anonymousChatMessageController;

    @Autowired
    private AnonymousMessageService anonymousMessageService;

    @MockitoBean
    private AnonymousMessageEventPublisher anonymousMessageEventPublisher;

    private final List<StompSession> sessions = new ArrayList<>();
    private WebSocketStompClient stompClient;
    private Long questionerId;
    private Long recipientId;
    private Long outsiderId;

    @BeforeEach
    void setUp() {
        willAnswer(invocation -> {
            AnonymousMessageRedisEvent event = invocation.getArgument(0);
            eventSender.sendToUser(event.receiverUserId(), event.event());
            return null;
        }).given(anonymousMessageEventPublisher).publish(any());

        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new JacksonJsonMessageConverter());

        questionerId = saveUser(9001L).getId();
        recipientId = saveUser(9002L).getId();
        outsiderId = saveUser(9003L).getId();
    }

    @AfterEach
    void tearDown() {
        sessions.stream().filter(StompSession::isConnected).forEach(StompSession::disconnect);
        stompClient.stop();
        anonymousMessageRepository.deleteAll();
        anonymousRoomRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void participantMessage_send_sendsSentToSenderAndReceivedToPartner() throws Exception {
        AnonymousRoom room = saveRoom();
        ConnectedClient sender = connectAndSubscribe(questionerId);
        ConnectedClient partner = connectAndSubscribe(recipientId);
        UUID clientMessageId = UUID.randomUUID();

        sender.send(room.getId(), messageBody(clientMessageId, "안녕하세요"));

        JsonNode sent = sender.nextEvent();
        assertThat(sent).isNotNull();
        assertThat(sent.get("type").asString()).isEqualTo("ANONYMOUS_CHAT_MESSAGE_SENT");
        assertThat(sent.has("requestId")).isFalse();
        assertThat(sent.get("data").get("roomId").asLong()).isEqualTo(room.getId());
        assertThat(sent.get("data").get("clientMessageId").asString()).isEqualTo(clientMessageId.toString());
        assertThat(sent.get("data").get("content").asString()).isEqualTo("안녕하세요");
        assertThat(sent.get("data").hasNonNull("sentAt")).isTrue();
        long messageId = sent.get("data").get("messageId").asLong();

        JsonNode received = partner.nextEvent();
        assertThat(received).isNotNull();
        assertThat(received.get("type").asString()).isEqualTo("ANONYMOUS_CHAT_MESSAGE_RECEIVED");
        assertThat(received.has("requestId")).isFalse();
        assertThat(received.get("data").get("messageId").asLong()).isEqualTo(messageId);
        assertThat(received.get("data").get("sender").asString()).isEqualTo("OTHER");
        assertThat(received.get("data").get("content").asString()).isEqualTo("안녕하세요");
        assertThat(received.get("data").has("senderUserId")).isFalse();

        assertThat(anonymousMessageRepository.findById(messageId)).get()
                .extracting(AnonymousMessage::getContent)
                .isEqualTo("안녕하세요");
    }

    @Test
    void sameClientMessageId_send_resendsSentWithoutSavingOrNotifyingPartner() throws Exception {
        AnonymousRoom room = saveRoom();
        ConnectedClient sender = connectAndSubscribe(questionerId);
        ConnectedClient partner = connectAndSubscribe(recipientId);
        UUID clientMessageId = UUID.randomUUID();
        sender.send(room.getId(), messageBody(clientMessageId, "안녕하세요"));
        long messageId = sender.nextEvent().get("data").get("messageId").asLong();
        partner.nextEvent();

        sender.send(room.getId(), messageBody(clientMessageId, "안녕하세요"));

        JsonNode resent = sender.nextEvent();
        assertThat(resent).isNotNull();
        assertThat(resent.get("type").asString()).isEqualTo("ANONYMOUS_CHAT_MESSAGE_SENT");
        assertThat(resent.get("data").get("clientMessageId").asString()).isEqualTo(clientMessageId.toString());
        assertThat(resent.get("data").get("messageId").asLong()).isEqualTo(messageId);
        assertThat(partner.eventWithinNoEventWait()).isNull();
        assertThat(anonymousMessageRepository.count()).isEqualTo(1);
    }

    @Test
    void senderWithOtherTab_send_sendsSentToEveryTabOfSender() throws Exception {
        AnonymousRoom room = saveRoom();
        ConnectedClient sendingTab = connectAndSubscribe(questionerId);
        ConnectedClient otherTab = connectAndSubscribe(questionerId);

        sendingTab.send(room.getId(), messageBody(UUID.randomUUID(), "안녕하세요"));

        assertThat(sendingTab.nextEvent().get("type").asString()).isEqualTo("ANONYMOUS_CHAT_MESSAGE_SENT");
        JsonNode otherTabEvent = otherTab.nextEvent();
        assertThat(otherTabEvent).isNotNull();
        assertThat(otherTabEvent.get("type").asString()).isEqualTo("ANONYMOUS_CHAT_MESSAGE_SENT");
        assertThat(otherTabEvent.get("data").get("content").asString()).isEqualTo("안녕하세요");
    }

    @Test
    void blankContent_send_sendsInvalidInputErrorOnlyToRequestingTab() throws Exception {
        AnonymousRoom room = saveRoom();
        ConnectedClient requestingTab = connectAndSubscribe(questionerId);
        ConnectedClient otherTab = connectAndSubscribe(questionerId);

        UUID clientMessageId = UUID.randomUUID();

        requestingTab.send(room.getId(), messageBody(clientMessageId, "  "));

        assertError(requestingTab.nextEvent(), "COMMON_INVALID_INPUT", room.getId(), clientMessageId.toString());
        assertThat(otherTab.eventWithinNoEventWait()).isNull();
        assertThat(anonymousMessageRepository.count()).isZero();
    }

    @Test
    void contentOverMaxLength_send_sendsInvalidInputError() throws Exception {
        AnonymousRoom room = saveRoom();
        ConnectedClient sender = connectAndSubscribe(questionerId);
        UUID clientMessageId = UUID.randomUUID();
        String content = "가".repeat(SendAnonymousMessageRequest.CONTENT_MAX_LENGTH + 1);

        sender.send(room.getId(), messageBody(clientMessageId, content));

        assertError(sender.nextEvent(), "COMMON_INVALID_INPUT", room.getId(), clientMessageId.toString());
    }

    @Test
    void contentAtMaxLength_send_sendsSent() throws Exception {
        AnonymousRoom room = saveRoom();
        ConnectedClient sender = connectAndSubscribe(questionerId);
        String content = "가".repeat(SendAnonymousMessageRequest.CONTENT_MAX_LENGTH);

        sender.send(room.getId(), messageBody(UUID.randomUUID(), content));

        assertThat(sender.nextEvent().get("type").asString()).isEqualTo("ANONYMOUS_CHAT_MESSAGE_SENT");
    }

    @Test
    void malformedClientMessageId_send_sendsInvalidRequestErrorWithRequestId() throws Exception {
        AnonymousRoom room = saveRoom();
        ConnectedClient sender = connectAndSubscribe(questionerId);

        sender.send(room.getId(),
                Map.of("clientMessageId", "not-uuid", "content", "안녕하세요"));

        assertError(sender.nextEvent(), "COMMON_INVALID_REQUEST", room.getId(), "not-uuid");
    }

    @Test
    void roomIdNotNumber_send_sendsInvalidRequestErrorWithoutRoomId() throws Exception {
        ConnectedClient sender = connectAndSubscribe(questionerId);
        UUID clientMessageId = UUID.randomUUID();

        sender.sendTo("/app/anonymous-chats/abc/messages", messageBody(clientMessageId, "안녕하세요"));

        assertError(sender.nextEvent(), "COMMON_INVALID_REQUEST", null, clientMessageId.toString());
    }

    @Test
    void nonParticipant_send_sendsForbiddenError() throws Exception {
        AnonymousRoom room = saveRoom();
        ConnectedClient outsider = connectAndSubscribe(outsiderId);

        UUID clientMessageId = UUID.randomUUID();

        outsider.send(room.getId(), messageBody(clientMessageId, "안녕하세요"));

        assertError(outsider.nextEvent(), "ANONYMOUS_CHAT_ROOM_FORBIDDEN", room.getId(), clientMessageId.toString());
    }

    @Test
    void endedRoom_send_sendsRoomEndedErrorWithoutSaving() throws Exception {
        AnonymousRoom room = saveRoom();
        room.end(recipientId, LocalDateTime.now());
        anonymousRoomRepository.saveAndFlush(room);
        ConnectedClient sender = connectAndSubscribe(questionerId);

        UUID clientMessageId = UUID.randomUUID();

        sender.send(room.getId(), messageBody(clientMessageId, "안녕하세요"));

        assertError(sender.nextEvent(), "ANONYMOUS_CHAT_ROOM_ENDED", room.getId(), clientMessageId.toString());
        assertThat(anonymousMessageRepository.count()).isZero();
    }

    @Test
    void partnerReadsMessage_readMessages_sendsReadEventOnlyToSender() throws Exception {
        AnonymousRoom room = saveRoom();
        ConnectedClient sender = connectAndSubscribe(questionerId);
        ConnectedClient reader = connectAndSubscribe(recipientId);
        long messageId = sendAndAwaitReceived(sender, reader, room);

        anonymousMessageService.readMessages(recipientId, room.getId(), messageId);

        JsonNode read = sender.nextEvent();
        assertThat(read).isNotNull();
        assertThat(read.get("type").asString()).isEqualTo("ANONYMOUS_CHAT_MESSAGE_READ");
        assertThat(read.get("data").get("roomId").asLong()).isEqualTo(room.getId());
        assertThat(read.get("data").get("lastReadMessageId").asLong()).isEqualTo(messageId);
        assertThat(read.get("data").hasNonNull("readAt")).isTrue();
        assertThat(reader.eventWithinNoEventWait()).isNull();
    }

    @Test
    void sameReadPosition_readMessages_doesNotSendReadEventAgain() throws Exception {
        AnonymousRoom room = saveRoom();
        ConnectedClient sender = connectAndSubscribe(questionerId);
        ConnectedClient reader = connectAndSubscribe(recipientId);
        long messageId = sendAndAwaitReceived(sender, reader, room);
        anonymousMessageService.readMessages(recipientId, room.getId(), messageId);
        assertThat(sender.nextEvent().get("type").asString()).isEqualTo("ANONYMOUS_CHAT_MESSAGE_READ");

        anonymousMessageService.readMessages(recipientId, room.getId(), messageId);

        assertThat(sender.eventWithinNoEventWait()).isNull();
    }

    @Test
    void tooManyRequests_handleBusinessException_returnsRetryAfterWithMessageIdentity() {
        Message<byte[]> message = MessageBuilder
                .withPayload("{\"clientMessageId\":\"client-1\",\"content\":\"안녕하세요\"}".getBytes())
                .setHeader(DestinationVariableMethodArgumentResolver.DESTINATION_TEMPLATE_VARIABLES_HEADER,
                        Map.of("roomId", "7"))
                .build();

        AnonymousChatErrorEvent event = anonymousChatMessageController.handleBusinessException(
                new TooManyRequestsException(3), message);

        assertThat(event.code()).isEqualTo("COMMON_TOO_MANY_REQUESTS");
        assertThat(event.data().roomId()).isEqualTo(7L);
        assertThat(event.data().clientMessageId()).isEqualTo("client-1");
        assertThat(event.data().retryAfterSeconds()).isEqualTo(3L);
    }

    // 보낸 쪽의 SENT와 받은 쪽의 RECEIVED를 모두 비워 두고 저장된 messageId를 돌려준다.
    private static long sendAndAwaitReceived(ConnectedClient sender, ConnectedClient receiver, AnonymousRoom room)
            throws InterruptedException {
        sender.send(room.getId(), messageBody(UUID.randomUUID(), "안녕하세요"));
        long messageId = sender.nextEvent().get("data").get("messageId").asLong();
        assertThat(receiver.nextEvent().get("data").get("messageId").asLong()).isEqualTo(messageId);
        return messageId;
    }

    private ConnectedClient connectAndSubscribe(Long userId) throws Exception {
        WebSocketHttpHeaders handshakeHeaders = new WebSocketHttpHeaders();
        handshakeHeaders.add(HttpHeaders.COOKIE,
                AuthCookieNames.ACCESS_TOKEN + "=" + accessTokenService.issueAccessToken(userId));
        StompSession session = stompClient.connectAsync(
                "ws://localhost:" + port + "/ws/v1/anonymous-chats",
                handshakeHeaders,
                new StompSessionHandlerAdapter() {
                }
        ).get(EVENT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        sessions.add(session);

        BlockingQueue<JsonNode> events = new LinkedBlockingQueue<>();
        session.subscribe(USER_QUEUE, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return JsonNode.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                events.add((JsonNode) payload);
            }
        });
        ConnectedClient client = new ConnectedClient(session, events);
        awaitSubscribed(client);
        return client;
    }

    // 간이 브로커는 구독 완료 응답(RECEIPT)을 보내지 않아서,
    // 존재하지 않는 방으로 보낸 요청의 오류 이벤트가 돌아오는 것으로 구독이 등록됐는지 확인한다.
    private void awaitSubscribed(ConnectedClient client) throws InterruptedException {
        for (int attempt = 0; attempt < SUBSCRIBE_CHECK_ATTEMPTS; attempt++) {
            String clientMessageId = UUID.randomUUID().toString();
            client.send(Long.MAX_VALUE, Map.of("clientMessageId", clientMessageId, "content", "확인"));
            JsonNode event = client.events().poll(SUBSCRIBE_CHECK_WAIT_MILLIS, TimeUnit.MILLISECONDS);
            if (event != null && clientMessageId.equals(event.get("data").get("clientMessageId").asString())) {
                // 앞선 확인 요청의 늦은 응답이 남아 있을 수 있어 잠시 기다린 뒤 비운다.
                Thread.sleep(SUBSCRIBE_CHECK_WAIT_MILLIS);
                client.events().clear();
                return;
            }
        }
        throw new IllegalStateException("STOMP subscription was not registered.");
    }

    private static Map<String, Object> messageBody(UUID clientMessageId, String content) {
        return Map.of("clientMessageId", clientMessageId, "content", content);
    }

    private static void assertError(JsonNode event, String code, Long roomId, String clientMessageId) {
        assertThat(event).isNotNull();
        assertThat(event.get("type").asString()).isEqualTo("ANONYMOUS_CHAT_ERROR");
        assertThat(event.has("requestId")).isFalse();
        assertThat(event.get("code").asString()).isEqualTo(code);
        assertThat(event.get("message").asString()).isNotBlank();
        JsonNode data = event.get("data");
        if (roomId == null) {
            assertThat(data.get("roomId").isNull()).isTrue();
        } else {
            assertThat(data.get("roomId").asLong()).isEqualTo(roomId);
        }
        assertThat(data.get("clientMessageId").asString()).isEqualTo(clientMessageId);
        assertThat(data.has("retryAfterSeconds")).isFalse();
    }

    private AnonymousRoom saveRoom() {
        return anonymousRoomRepository.saveAndFlush(
                new AnonymousRoom(1L, questionerId, recipientId, "익명의 수달"));
    }

    private User saveUser(Long externalId) {
        LocalDate birthDate = LocalDate.of(2000, 1, 1);
        User user = new User(externalId, "사용자", null, Gender.NONE, birthDate);
        user.completeOnboarding(Gender.NONE, birthDate);
        return userRepository.saveAndFlush(user);
    }

    private record ConnectedClient(StompSession session, BlockingQueue<JsonNode> events) {

        void send(Long roomId, Map<String, Object> body) {
            sendTo("/app/anonymous-chats/" + roomId + "/messages", body);
        }

        void sendTo(String destination, Map<String, Object> body) {
            StompHeaders headers = new StompHeaders();
            headers.setDestination(destination);
            headers.setContentType(MimeTypeUtils.APPLICATION_JSON);
            session.send(headers, body);
        }

        JsonNode nextEvent() throws InterruptedException {
            return events.poll(EVENT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }

        JsonNode eventWithinNoEventWait() throws InterruptedException {
            return events.poll(NO_EVENT_WAIT_MILLIS, TimeUnit.MILLISECONDS);
        }
    }
}
