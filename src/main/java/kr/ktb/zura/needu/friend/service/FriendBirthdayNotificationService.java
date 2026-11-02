package kr.ktb.zura.needu.friend.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
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
    private static final int LEAP_DAY_MONTH_DAY = 229;
    private static final String BATCH_TIMER = "needu.friend.birthday.batch";

    private final FriendRepository friendRepository;
    private final NotificationCreationService notificationCreationService;
    private final MeterRegistry meterRegistry;
    private final int reminderDays;
    private final ZoneId notificationZone;

    public FriendBirthdayNotificationService(
            FriendRepository friendRepository,
            NotificationCreationService notificationCreationService,
            MeterRegistry meterRegistry,
            @Value("${needu.notification.friend-birthday.reminder-days}") int reminderDays,
            @Value("${needu.notification.friend-birthday.zone}") String notificationZone
    ) {
        this.friendRepository = friendRepository;
        this.notificationCreationService = notificationCreationService;
        this.meterRegistry = meterRegistry;
        this.reminderDays = reminderDays;
        this.notificationZone = ZoneId.of(notificationZone);
    }

    public void createNotifications(LocalDate executionDate) {
        Timer.Sample totalSample = Timer.start(meterRegistry);
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

        long totalNanos = totalSample.stop(batchTimer("total"));
        log.info("Friend birthday notification batch completed. receiverCount={}, friendCount={}, "
                        + "notificationCount={}, totalMs={}",
                receiverUserIdsByFriend.values().stream().mapToInt(Set::size).sum(), receiverUserIdsByFriend.size(),
                notificationCount, Duration.ofNanos(totalNanos).toMillis());
    }

    // 조회 결과 목록은 이 메서드 안에서만 참조해 그룹핑 뒤 바로 GC 대상이 되게 한다 (300만 행 규모)
    private Map<BirthdayFriend, Set<Long>> findReceiverUserIdsByFriend(LocalDate birthdayDate, boolean includeLeapDay) {
        // shortcut: 하루 대상 관계를 한 번에 조회한다, 메모리 부담이 커지면 keyset batch로 전환
        Timer.Sample querySample = Timer.start(meterRegistry);
        int birthMonthDay = birthdayDate.getMonthValue() * 100 + birthdayDate.getDayOfMonth();
        List<Integer> birthMonthDays = includeLeapDay
                ? List.of(birthMonthDay, LEAP_DAY_MONTH_DAY)
                : List.of(birthMonthDay);
        List<FriendBirthdayNotificationTarget> targets =
                friendRepository.findAllBirthdayNotificationTargets(birthMonthDays);
        querySample.stop(batchTimer("query"));
        return targets.stream()
                .collect(Collectors.groupingBy(
                        target -> new BirthdayFriend(target.friendUserId(), target.nickname()),
                        LinkedHashMap::new,
                        Collectors.mapping(FriendBirthdayNotificationTarget::ownerUserId, Collectors.toSet())
                ));
    }

    private Timer batchTimer(String stage) {
        return Timer.builder(BATCH_TIMER)
                .tag("stage", stage)
                .register(meterRegistry);
    }

    private record BirthdayFriend(Long userId, String nickname) {
    }
}
