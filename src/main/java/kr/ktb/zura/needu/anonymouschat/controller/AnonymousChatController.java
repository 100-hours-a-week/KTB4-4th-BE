package kr.ktb.zura.needu.anonymouschat.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/anonymous-chats")
public class AnonymousChatController {

    @GetMapping
    public void findAllRooms() {
    }

    @PostMapping("/friends/{userId}")
    public void createRoom() {
    }

    @GetMapping("/{roomId}")
    public void findRoom() {
    }

    @GetMapping("/{roomId}/messages")
    public void findAllMessages() {
    }

    @PatchMapping("/{roomId}/read")
    public void readMessages() {
    }

    @PostMapping("/{roomId}/reports")
    public void reportChat() {
    }

    @PutMapping("/{roomId}/leave")
    public void leaveRoom() {
    }
}
