package kr.ktb.zura.needu.anonymouschat.ws;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class AnonymousChatEventSender {

    public static final String USER_DESTINATION = "/queue/anonymous-chats";

    private final SimpMessagingTemplate messagingTemplate;

    public AnonymousChatEventSender(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void sendToUser(Long userId, Object event) {
        messagingTemplate.convertAndSendToUser(String.valueOf(userId), USER_DESTINATION, event);
    }
}
