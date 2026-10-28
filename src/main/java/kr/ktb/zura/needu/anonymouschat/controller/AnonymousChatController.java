package kr.ktb.zura.needu.anonymouschat.controller;

import jakarta.validation.Valid;
import kr.ktb.zura.needu.anonymouschat.dto.request.AnonymousRoomSearchCondition;
import kr.ktb.zura.needu.anonymouschat.dto.request.CreateAnonymousReportRequest;
import kr.ktb.zura.needu.anonymouschat.dto.request.CreateAnonymousRoomRequest;
import kr.ktb.zura.needu.anonymouschat.dto.request.ReadAnonymousMessagesRequest;
import kr.ktb.zura.needu.anonymouschat.dto.response.AnonymousChatRoomSummaryResponse;
import kr.ktb.zura.needu.anonymouschat.dto.response.AnonymousReportResponse;
import kr.ktb.zura.needu.anonymouschat.dto.response.AnonymousRoomCreatedResponse;
import kr.ktb.zura.needu.anonymouschat.dto.response.AnonymousRoomDetailResponse;
import kr.ktb.zura.needu.anonymouschat.service.AnonymousChatService;
import kr.ktb.zura.needu.anonymouschat.service.AnonymousMessageService;
import kr.ktb.zura.needu.anonymouschat.service.AnonymousReportService;
import kr.ktb.zura.needu.common.response.ApiResponse;
import kr.ktb.zura.needu.common.response.CursorApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
    private final AnonymousReportService anonymousReportService;

    @GetMapping
    public ResponseEntity<CursorApiResponse<AnonymousChatRoomSummaryResponse>> findAllRooms(
            @AuthenticationPrincipal Long userId,
            @Valid AnonymousRoomSearchCondition condition
    ) {
        return ResponseEntity.ok(CursorApiResponse.of(
                AnonymousChatResponseMessages.ROOMS_FOUND,
                anonymousChatService.findAllRooms(userId, condition)
        ));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AnonymousRoomCreatedResponse>> createRoom(
            @AuthenticationPrincipal Long questionerUserId,
            @RequestBody @Valid CreateAnonymousRoomRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(
                        AnonymousChatResponseMessages.ROOM_CREATED,
                        anonymousChatService.createRoom(
                                questionerUserId, request.recipientUserId(), request.productId())
                ));
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
    public ResponseEntity<ApiResponse<AnonymousReportResponse>> reportChat(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long roomId,
            @RequestBody @Valid CreateAnonymousReportRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(
                AnonymousChatResponseMessages.REPORT_CREATED,
                anonymousReportService.reportRoom(userId, roomId, request)
        ));
    }

    @PutMapping("/{roomId}/leave")
    public void leaveRoom() {
    }
}
