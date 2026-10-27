package kr.ktb.zura.needu.anonymouschat.controller;

import jakarta.validation.Valid;
import kr.ktb.zura.needu.anonymouschat.dto.request.ReadAnonymousMessagesRequest;
import kr.ktb.zura.needu.anonymouschat.dto.response.AnonymousRoomDetailResponse;
import kr.ktb.zura.needu.anonymouschat.service.AnonymousChatService;
import kr.ktb.zura.needu.anonymouschat.service.AnonymousMessageService;
import kr.ktb.zura.needu.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/anonymous-chats")
public class AnonymousChatController {

    private final AnonymousChatService anonymousChatService;
    private final AnonymousMessageService anonymousMessageService;

    @GetMapping
    public void findAllRooms() {
    }

    @PostMapping("/friends/{userId}")
    public void createRoom() {
    }

    @GetMapping("/{roomId}")
    public ResponseEntity<ApiResponse<AnonymousRoomDetailResponse>> findRoom(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long roomId
    ) {
        return ResponseEntity.ok(ApiResponse.of(
                AnonymousChatResponseMessages.ROOM_FOUND,
                anonymousChatService.findRoom(userId, roomId)
        ));
    }

    @GetMapping("/{roomId}/messages")
    public void findAllMessages() {
    }

    @PatchMapping("/{roomId}/read")
    public ResponseEntity<Void> readMessages(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long roomId,
            @RequestBody @Valid ReadAnonymousMessagesRequest request
    ) {
        anonymousMessageService.readMessages(userId, roomId, request.lastReadMessageId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{roomId}/reports")
    public void reportChat() {
    }

    @PutMapping("/{roomId}/leave")
    public void leaveRoom() {
    }
}
