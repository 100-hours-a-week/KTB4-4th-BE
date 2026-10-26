package kr.ktb.zura.needu.anonymouschat.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AnonymousChatController {

    @GetMapping("/anonymous-chats")
    public void findAllRooms() {
    }

    @PostMapping("/friends/{userId}/anonymous-chat-rooms")
    public void createRoom() {
    }

    @GetMapping("/anonymous-chats/{roomId}")
    public void findRoom() {
    }

    @GetMapping("/anonymous-chats/{roomId}/messages")
    public void findAllMessages() {
    }

    @PatchMapping("/anonymous-chats/{roomId}/read")
    public void readMessages() {
    }

    @PostMapping("/anonymous-chats/{roomId}/reports")
    public void reportChat() {
    }

    @PutMapping("/anonymous-chats/{roomId}/leave")
    public void leaveRoom() {
    }
}
