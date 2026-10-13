package kr.ktb.zura.needu.friend.service;

import java.net.URI;
import kr.ktb.zura.needu.auth.service.AuthService;
import kr.ktb.zura.needu.friend.client.KakaoFriendClient;
import kr.ktb.zura.needu.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KakaoFriendSyncService {

    private final AuthService authService;
    private final UserService userService;
    private final KakaoFriendClient kakaoFriendClient;
    private final FriendService friendService;

    public URI createAuthorizationUri(Long userId, String state) {
        userService.validateActiveUser(userId);
        return authService.createKakaoFriendAuthorizationUri(state);
    }

    public void sync(Long userId, String code) {
        userService.validateActiveUser(userId);
        String accessToken = authService.authorizeKakaoFriend(userId, code);
        friendService.syncKakaoFriends(userId,
                kakaoFriendClient.findAllFriendIds(accessToken));
    }
}
