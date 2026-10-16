package kr.ktb.zura.needu.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import kr.ktb.zura.needu.notification.entity.NotificationEvent;
import kr.ktb.zura.needu.notification.repository.NotificationEventRepository;
import kr.ktb.zura.needu.notification.repository.NotificationRepository;
import kr.ktb.zura.needu.notification.repository.NotificationSettingRepository;
import kr.ktb.zura.needu.notification.repository.PushSubscriptionRepository;
import kr.ktb.zura.needu.notification.type.NotificationResourceType;
import kr.ktb.zura.needu.notification.type.NotificationType;
import org.junit.jupiter.api.Test;

class NotificationWithdrawalServiceTest {

    private final NotificationRepository notificationRepository = mock(NotificationRepository.class);
    private final NotificationEventRepository eventRepository = mock(NotificationEventRepository.class);
    private final NotificationSettingRepository settingRepository = mock(NotificationSettingRepository.class);
    private final PushSubscriptionRepository subscriptionRepository = mock(PushSubscriptionRepository.class);
    private final NotificationWithdrawalService service = new NotificationWithdrawalService(
            notificationRepository, eventRepository, settingRepository, subscriptionRepository);

    @Test
    void userDataExists_deleteUserData_deletesOwnedDataAndAnonymizesSentEvents() {
        NotificationEvent event = NotificationEvent.create(
                NotificationType.POKE,
                1L,
                "니듀님이 콕 찔렀어요.",
                "니듀님의 알림입니다.",
                NotificationResourceType.AI_CONVERSATION,
                null,
                "poke:1:request-id"
        );
        when(eventRepository.findAllByActorUserId(1L)).thenReturn(List.of(event));

        service.deleteUserData(1L, "니듀");

        verify(notificationRepository).deleteAllByReceiverUserId(1L);
        verify(settingRepository).deleteAllByUserId(1L);
        verify(subscriptionRepository).deleteAllByUserId(1L);
        assertThat(event.getActorUserId()).isNull();
        assertThat(event.getTitle()).isEqualTo("탈퇴한 사용자님이 콕 찔렀어요.");
        assertThat(event.getBody()).isEqualTo("탈퇴한 사용자님의 알림입니다.");
    }
}
