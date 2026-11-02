package kr.ktb.zura.needu.friend.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import kr.ktb.zura.needu.friend.dto.response.FriendBirthdayNotificationTarget;
import kr.ktb.zura.needu.friend.repository.FriendRepository;
import kr.ktb.zura.needu.notification.service.NotificationCreationService;
import kr.ktb.zura.needu.notification.type.NotificationResourceType;
import kr.ktb.zura.needu.notification.type.NotificationType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class FriendBirthdayNotificationService {

    private static final String BODY = "지금 선물을 준비하면 여유 있게 도착해요.";

    private final FriendRepository friendRepository;
    private final NotificationCreationService notificationCreationService;
    private final int reminderDays;
    private final ZoneId notificationZone;

    public FriendBirthdayNotificationService(
            FriendRepository friendRepository,
            NotificationCreationService notificationCreationService,
            @Value("${needu.notification.friend-birthday.reminder-days}") int reminderDays,
            @Value("${needu.notification.friend-birthday.zone}") String notificationZone
    ) {
        this.friendRepository = friendRepository;
        this.notificationCreationService = notificationCreationService;
        this.reminderDays = reminderDays;
        this.notificationZone = ZoneId.of(notificationZone);
    }

    public void createNotifications(LocalDate executionDate) {
        LocalDate birthdayDate = executionDate.plusDays(reminderDays);
        boolean includeLeapDay = !birthdayDate.isLeapYear()
                && birthdayDate.getMonthValue() == 3
                && birthdayDate.getDayOfMonth() == 1;
        Map<BirthdayFriend, Set<Long>> receiverUserIdsByFriend =
                findReceiverUserIdsByFriend(birthdayDate, includeLeapDay);
        LocalDateTime expiresAt = birthdayDate.plusDays(1)
                .atStartOfDay(notificationZone)
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();

        int notificationCount = 0;
        for (Map.Entry<BirthdayFriend, Set<Long>> entry : receiverUserIdsByFriend.entrySet()) {
            BirthdayFriend friend = entry.getKey();
            notificationCount += notificationCreationService.createNotifications(
                    entry.getValue(),
                    NotificationType.FRIEND_BIRTHDAY,
                    null,
                    friend.nickname() + " 님의 생일이 " + reminderDays + "일 남았어요.",
                    BODY,
                    NotificationResourceType.USER,
                    friend.userId(),
                    "friend-birthday:" + birthdayDate + ":" + friend.userId() + ":d-" + reminderDays,
                    expiresAt
            );
        }

        log.info("Friend birthday notification batch completed. receiverCount={}, friendCount={}, notificationCount={}",
                receiverUserIdsByFriend.values().stream().mapToInt(Set::size).sum(), receiverUserIdsByFriend.size(),
                notificationCount);
    }

    // 조회 결과 목록은 이 메서드 안에서만 참조해 그룹핑 뒤 바로 GC 대상이 되게 한다 (300만 행 규모)
    private Map<BirthdayFriend, Set<Long>> findReceiverUserIdsByFriend(LocalDate birthdayDate, boolean includeLeapDay) {
        // shortcut: 하루 대상 관계를 한 번에 조회한다, 메모리 부담이 커지면 keyset batch로 전환
        List<FriendBirthdayNotificationTarget> targets = friendRepository.findAllBirthdayNotificationTargets(
                birthdayDate.getMonthValue(), birthdayDate.getDayOfMonth(), includeLeapDay);
        return targets.stream()
                .collect(Collectors.groupingBy(
                        target -> new BirthdayFriend(target.friendUserId(), target.nickname()),
                        LinkedHashMap::new,
                        Collectors.mapping(FriendBirthdayNotificationTarget::ownerUserId, Collectors.toSet())
                ));
    }

    private record BirthdayFriend(Long userId, String nickname) {
    }
}
