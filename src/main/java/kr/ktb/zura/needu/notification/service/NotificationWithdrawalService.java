package kr.ktb.zura.needu.notification.service;

import kr.ktb.zura.needu.notification.repository.NotificationEventRepository;
import kr.ktb.zura.needu.notification.repository.NotificationRepository;
import kr.ktb.zura.needu.notification.repository.NotificationSettingRepository;
import kr.ktb.zura.needu.notification.repository.PushSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationWithdrawalService {

    private final NotificationRepository notificationRepository;
    private final NotificationEventRepository notificationEventRepository;
    private final NotificationSettingRepository notificationSettingRepository;
    private final PushSubscriptionRepository pushSubscriptionRepository;

    @Transactional
    public void deleteUserData(Long userId, String nickname) {
        notificationRepository.deleteAllByReceiverUserId(userId);
        notificationEventRepository.findAllByActorUserId(userId)
                .forEach(event -> event.anonymizeActor(nickname));
        notificationSettingRepository.deleteAllByUserId(userId);
        pushSubscriptionRepository.deleteAllByUserId(userId);
    }
}
