package kr.ktb.zura.needu.friend.controller;

import kr.ktb.zura.needu.common.response.ApiResponse;
import kr.ktb.zura.needu.friend.dto.response.FriendResponse;
import kr.ktb.zura.needu.friend.service.FriendService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/friends")
public class FriendController {

    private static final String FRIEND_FOUND_MESSAGE = "친구 정보를 조회했습니다.";

    private final FriendService friendService;

    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<FriendResponse>> findFriend(
            @AuthenticationPrincipal Long loginUserId,
            @PathVariable Long userId
    ) {
        return ResponseEntity.ok(ApiResponse.of(FRIEND_FOUND_MESSAGE, friendService.findFriend(loginUserId, userId)));
    }
}
