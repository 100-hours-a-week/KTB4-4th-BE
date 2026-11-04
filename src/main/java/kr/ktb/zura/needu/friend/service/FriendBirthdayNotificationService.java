package kr.ktb.zura.needu.friend.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
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
import kr.ktb.zura.needu.user.dto.response.UserSummaryResponse;
import kr.ktb.zura.needu.user.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class FriendBirthdayNotificationService {

    private static final String BODY = "지금 선물을 준비하면 여유 있게 도착해요.";
    private static final int LEAP_DAY_MONTH_DAY = 229;

    private final FriendRepository friendRepository;
    private final UserService userService;
    private final NotificationCreationService notificationCreationService;
    private final int reminderDays;
    private final ZoneId notificationZone;
    private final int chunkSize;

    public FriendBirthdayNotificationService(
            FriendRepository friendRepository,
            UserService userService,
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
        this.userService = userService;
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
        int birthMonthDay = birthdayDate.getMonthValue() * 100 + birthdayDate.getDayOfMonth();
        List<Integer> birthMonthDays = includeLeapDay
                ? List.of(birthMonthDay, LEAP_DAY_MONTH_DAY)
                : List.of(birthMonthDay);
        LocalDateTime expiresAt = birthdayDate.plusDays(1)
                .atStartOfDay(notificationZone)
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();

        int receiverCount = 0;
        int friendCount = 0;
        int notificationCount = 0;
        int failedFriendCount = 0;
        // 생일 친구를 id 순서로 chunk만큼 읽고 바로 알림을 만든다. 하루 대상 전체를 메모리에 올리지 않는다
        // 한 친구의 수신자는 항상 같은 chunk에 들어간다 (나뉘면 뒤쪽 수신자가 dedupKey에 걸려 빠진다)
        Long cursorUserId = 0L;
        while (true) {
            List<UserSummaryResponse> birthdayFriends =
                    userService.findAllActiveUsersByBirthMonthDays(birthMonthDays, cursorUserId, chunkSize);
            if (birthdayFriends.isEmpty()) {
                break;
            }
            cursorUserId = birthdayFriends.getLast().id();

            List<NotificationCreationCommand> commands = toCommands(birthdayFriends, birthdayDate, expiresAt);
            if (!commands.isEmpty()) {
                friendCount += commands.size();
                receiverCount += commands.stream().mapToInt(command -> command.receiverUserIds().size()).sum();
                // 생일 친구 chunk마다 트랜잭션 하나로 만들어 커밋·사전 조회·unread count 왕복을 chunk 수만큼으로 줄인다
                try {
                    notificationCount += notificationCreationService.createNotifications(commands);
                } catch (RuntimeException exception) {
                    // 실패한 chunk만 롤백되고 다음 chunk는 계속 만든다. 다시 실행하면 dedupKey로 이미 만든 알림은 건너뛴다
                    failedFriendCount += commands.size();
                    log.warn("Friend birthday notification chunk failed. birthdayDate={}, firstFriendUserId={}, friendCount={}",
                            birthdayDate, commands.getFirst().resourceId(), commands.size(), exception);
                }
            }
            if (birthdayFriends.size() < chunkSize) {
                break;
            }
        }

        log.info("Friend birthday notification batch completed. receiverCount={}, friendCount={}, "
                        + "notificationCount={}, failedFriendCount={}",
                receiverCount, friendCount, notificationCount, failedFriendCount);
    }

    // 알림을 받을 활성 친구가 없는 생일 친구는 command를 만들지 않는다
    private List<NotificationCreationCommand> toCommands(
            List<UserSummaryResponse> birthdayFriends,
            LocalDate birthdayDate,
            LocalDateTime expiresAt
    ) {
        List<Long> birthdayFriendUserIds = birthdayFriends.stream().map(UserSummaryResponse::id).toList();
        Map<Long, Set<Long>> receiverUserIdsByFriendUserId =
                friendRepository.findAllBirthdayNotificationTargets(birthdayFriendUserIds).stream()
                        .collect(Collectors.groupingBy(
                                FriendBirthdayNotificationTarget::friendUserId,
                                Collectors.mapping(FriendBirthdayNotificationTarget::ownerUserId, Collectors.toSet())
                        ));
        return birthdayFriends.stream()
                .filter(friend -> receiverUserIdsByFriendUserId.containsKey(friend.id()))
                .map(friend -> toCommand(friend, receiverUserIdsByFriendUserId.get(friend.id()), birthdayDate, expiresAt))
                .toList();
    }

    private NotificationCreationCommand toCommand(
            UserSummaryResponse friend,
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
                friend.id(),
                "friend-birthday:" + birthdayDate + ":" + friend.id() + ":d-" + reminderDays,
                expiresAt
        );
    }
}
