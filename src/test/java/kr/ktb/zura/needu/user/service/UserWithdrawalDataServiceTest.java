package kr.ktb.zura.needu.user.service;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import kr.ktb.zura.needu.aichat.service.AiChatWithdrawalService;
import kr.ktb.zura.needu.auth.service.AuthService;
import kr.ktb.zura.needu.friend.service.FriendWithdrawalService;
import kr.ktb.zura.needu.notification.service.NotificationWithdrawalService;
import kr.ktb.zura.needu.product.service.ProductWithdrawalService;
import kr.ktb.zura.needu.taste.service.TasteWithdrawalService;
import kr.ktb.zura.needu.user.dto.response.UserResponse;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class UserWithdrawalDataServiceTest {

    @Test
    void authenticatedUser_deleteAndAnonymize_deletesDomainDataBeforeUserAnonymization() {
        UserService userService = mock(UserService.class);
        AiChatWithdrawalService aiChatService = mock(AiChatWithdrawalService.class);
        TasteWithdrawalService tasteService = mock(TasteWithdrawalService.class);
        FriendWithdrawalService friendService = mock(FriendWithdrawalService.class);
        NotificationWithdrawalService notificationService = mock(NotificationWithdrawalService.class);
        ProductWithdrawalService productService = mock(ProductWithdrawalService.class);
        AuthService authService = mock(AuthService.class);
        UserWithdrawalDataService service = new UserWithdrawalDataService(
                userService,
                aiChatService,
                tasteService,
                friendService,
                notificationService,
                productService,
                authService
        );
        when(userService.findAuthenticatedUser(1L))
                .thenReturn(new UserResponse(1L, 42L, "니듀", null, true, null));
        when(aiChatService.findAllRoomIds(1L)).thenReturn(List.of(10L));

        service.deleteAndAnonymize(1L);

        InOrder order = inOrder(
                userService,
                aiChatService,
                tasteService,
                productService,
                notificationService,
                friendService,
                authService
        );
        order.verify(userService).findAuthenticatedUser(1L);
        order.verify(aiChatService).findAllRoomIds(1L);
        order.verify(tasteService).deleteAllByRoomIds(List.of(10L));
        order.verify(aiChatService).deleteUserData(1L);
        order.verify(productService).deleteUserData(1L);
        order.verify(notificationService).deleteUserData(1L, "니듀");
        order.verify(friendService).deleteUserData(1L);
        order.verify(authService).deleteUserAuthentication(1L);
        order.verify(userService).withdraw(1L);
    }
}
