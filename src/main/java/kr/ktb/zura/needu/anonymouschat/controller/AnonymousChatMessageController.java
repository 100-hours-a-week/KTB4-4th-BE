package kr.ktb.zura.needu.anonymouschat.controller;

import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

@Controller
public class AnonymousChatMessageController {

    @MessageMapping("/anonymous-chats/{roomId}/messages")
    public void sendMessage(@DestinationVariable Long roomId) {
        // shortcut: 메시지 처리 미구현, 익명대화 메시지 전송 작업에서 서비스 호출 추가
    }
}
