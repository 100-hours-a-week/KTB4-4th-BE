package kr.ktb.zura.needu.friend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.friend.dto.response.PokeResponse;
import kr.ktb.zura.needu.friend.exception.FriendErrorCode;
import kr.ktb.zura.needu.friend.repository.FriendRepository;
import kr.ktb.zura.needu.notification.service.NotificationCreationService;
import kr.ktb.zura.needu.notification.type.NotificationResourceType;
import kr.ktb.zura.needu.notification.type.NotificationType;
import kr.ktb.zura.needu.user.dto.response.UserSummaryResponse;
import kr.ktb.zura.needu.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PokeServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long FRIEND_USER_ID = 2L;

    private final UserService userService = mock(UserService.class);
    private final FriendRepository friendRepository = mock(FriendRepository.class);
    private final NotificationCreationService notificationCreationService = mock(NotificationCreationService.class);
    private final PokeService pokeService = new PokeService(
            userService,
            friendRepository,
            notificationCreationService
    );

    @Test
    void friendExists_createPoke_createsNotificationExpiringAfterThirtyDays() {
        given(userService.findUserSummary(USER_ID))
                .willReturn(new UserSummaryResponse(USER_ID, "지호", null, false));
        given(friendRepository.existsByOwnerUserIdAndFriendUserId(USER_ID, FRIEND_USER_ID)).willReturn(true);
        LocalDateTime beforeCreation = LocalDateTime.now(ZoneOffset.UTC);

        PokeResponse response = pokeService.createPoke(USER_ID, FRIEND_USER_ID);

        LocalDateTime afterCreation = LocalDateTime.now(ZoneOffset.UTC);
        ArgumentCaptor<String> dedupKeyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<LocalDateTime> expiresAtCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(notificationCreationService).createNotifications(
                org.mockito.ArgumentMatchers.eq(Set.of(FRIEND_USER_ID)),
                org.mockito.ArgumentMatchers.eq(NotificationType.POKE),
                org.mockito.ArgumentMatchers.eq(USER_ID),
                org.mockito.ArgumentMatchers.eq("지호님이 나를 콕 찔렀어요!"),
                org.mockito.ArgumentMatchers.eq("AI대화를 통해 취향 분석을 해보세요!"),
                org.mockito.ArgumentMatchers.eq(NotificationResourceType.AI_CONVERSATION),
                org.mockito.ArgumentMatchers.isNull(),
                dedupKeyCaptor.capture(),
                expiresAtCaptor.capture()
        );
        assertThat(response.createdAt()).isBetween(beforeCreation, afterCreation);
        assertThat(expiresAtCaptor.getValue()).isEqualTo(response.createdAt().plusDays(30));
        assertThatCode(() -> UUID.fromString(dedupKeyCaptor.getValue())).doesNotThrowAnyException();
    }

    @Test
    void notFriend_createPoke_throwsFriendNotFound() {
        given(userService.findUserSummary(USER_ID))
                .willReturn(new UserSummaryResponse(USER_ID, "지호", null, false));

        assertThatThrownBy(() -> pokeService.createPoke(USER_ID, FRIEND_USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(FriendErrorCode.FRIEND_NOT_FOUND);
        verify(notificationCreationService, never()).createNotifications(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }
}
