package kr.ktb.zura.needu.user.service;

import java.util.List;

import kr.ktb.zura.needu.aichat.service.AiChatWithdrawalService;
import kr.ktb.zura.needu.auth.service.AuthService;
import kr.ktb.zura.needu.friend.service.FriendWithdrawalService;
import kr.ktb.zura.needu.notification.service.NotificationWithdrawalService;
import kr.ktb.zura.needu.product.service.ProductWithdrawalService;
import kr.ktb.zura.needu.taste.service.TasteWithdrawalService;
import kr.ktb.zura.needu.user.dto.response.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserWithdrawalDataService {

    private final UserService userService;
    private final AiChatWithdrawalService aiChatWithdrawalService;
    private final TasteWithdrawalService tasteWithdrawalService;
    private final FriendWithdrawalService friendWithdrawalService;
    private final NotificationWithdrawalService notificationWithdrawalService;
    private final ProductWithdrawalService productWithdrawalService;
    private final AuthService authService;

    @Transactional
    public void deleteAndAnonymize(Long userId) {
        UserResponse user = userService.findAuthenticatedUser(userId);
        List<Long> roomIds = aiChatWithdrawalService.findAllRoomIds(userId);
        tasteWithdrawalService.deleteAllByRoomIds(roomIds);
        aiChatWithdrawalService.deleteUserData(userId);
        productWithdrawalService.deleteUserData(userId);
        notificationWithdrawalService.deleteUserData(userId, user.nickname());
        friendWithdrawalService.deleteUserData(userId);
        authService.deleteUserAuthentication(userId);
        userService.withdraw(userId);
    }
}
