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
import kr.ktb.zura.needu.notification.service.NotificationCreationCommand;
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
    private static final int LEAP_DAY_MONTH_DAY = 229;

    private final FriendRepository friendRepository;
    private final NotificationCreationService notificationCreationService;
    private final int reminderDays;
    private final ZoneId notificationZone;
    private final int chunkSize;

    public FriendBirthdayNotificationService(
            FriendRepository friendRepository,
            NotificationCreationService notificationCreationService,
            @Value("${needu.notification.friend-birthday.reminder-days}") int reminderDays,
            @Value("${needu.notification.friend-birthday.zone}") String notificationZone,
            @Value("${needu.notification.friend-birthday.chunk-size}") int chunkSize
    ) {
        // 0 이하면 chunk 반복이 끝나지 않으므로 기동 시점에 막는다
        if (chunkSize <= 0) {
            throw new IllegalArgumentException(
                    "needu.notification.friend-birthday.chunk-size must be positive. value=" + chunkSize);
        }
        this.friendRepository = friendRepository;
        this.notificationCreationService = notificationCreationService;
        this.reminderDays = reminderDays;
        this.notificationZone = ZoneId.of(notificationZone);
        this.chunkSize = chunkSize;
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

        List<NotificationCreationCommand> commands = receiverUserIdsByFriend.entrySet().stream()
                .map(entry -> toCommand(entry.getKey(), entry.getValue(), birthdayDate, expiresAt))
                .toList();
        int notificationCount = 0;
        int failedFriendCount = 0;
        // 생일 친구 chunk마다 트랜잭션 하나로 만들어 커밋·사전 조회·unread count 왕복을 chunk 수만큼으로 줄인다
        for (int fromIndex = 0; fromIndex < commands.size(); fromIndex += chunkSize) {
            List<NotificationCreationCommand> chunk =
                    commands.subList(fromIndex, Math.min(fromIndex + chunkSize, commands.size()));
            try {
                notificationCount += notificationCreationService.createNotifications(chunk);
            } catch (RuntimeException exception) {
                // 실패한 chunk만 롤백되고 다음 chunk는 계속 만든다. 다시 실행하면 dedupKey로 이미 만든 알림은 건너뛴다
                failedFriendCount += chunk.size();
                log.warn("Friend birthday notification chunk failed. birthdayDate={}, firstFriendUserId={}, friendCount={}",
                        birthdayDate, chunk.getFirst().resourceId(), chunk.size(), exception);
            }
        }

        log.info("Friend birthday notification batch completed. receiverCount={}, friendCount={}, "
                        + "notificationCount={}, failedFriendCount={}",
                receiverUserIdsByFriend.values().stream().mapToInt(Set::size).sum(), receiverUserIdsByFriend.size(),
                notificationCount, failedFriendCount);
    }

    private NotificationCreationCommand toCommand(
            BirthdayFriend friend,
            Set<Long> receiverUserIds,
            LocalDate birthdayDate,
            LocalDateTime expiresAt
    ) {
        return new NotificationCreationCommand(
                receiverUserIds,
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

    // 조회 결과 목록은 이 메서드 안에서만 참조해 그룹핑 뒤 바로 GC 대상이 되게 한다 (300만 행 규모)
    private Map<BirthdayFriend, Set<Long>> findReceiverUserIdsByFriend(LocalDate birthdayDate, boolean includeLeapDay) {
        // shortcut: 하루 대상 관계를 한 번에 조회한다, 메모리 부담이 커지면 keyset batch로 전환
        int birthMonthDay = birthdayDate.getMonthValue() * 100 + birthdayDate.getDayOfMonth();
        List<Integer> birthMonthDays = includeLeapDay
                ? List.of(birthMonthDay, LEAP_DAY_MONTH_DAY)
                : List.of(birthMonthDay);
        List<FriendBirthdayNotificationTarget> targets =
                friendRepository.findAllBirthdayNotificationTargets(birthMonthDays);
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
