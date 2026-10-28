package kr.ktb.zura.needu.anonymouschat.ws;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

class AnonymousChatEventSenderTest {

    @Test
    void userEvent_sendToUser_sendsToUserQueueByUserIdName() {
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        AnonymousChatEventSender sender = new AnonymousChatEventSender(messagingTemplate);
        Map<String, Object> event = Map.of("type", "ANONYMOUS_CHAT_MESSAGE_RECEIVED");

        sender.sendToUser(7L, event);

        verify(messagingTemplate).convertAndSendToUser("7", "/queue/anonymous-chats", event);
    }
}
