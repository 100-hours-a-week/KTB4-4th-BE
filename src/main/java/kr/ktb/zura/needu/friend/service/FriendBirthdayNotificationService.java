package kr.ktb.zura.needu.friend.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import kr.ktb.zura.needu.friend.dto.response.FriendBirthdayNotificationResult;
import kr.ktb.zura.needu.friend.dto.response.FriendBirthdayNotificationTarget;
import kr.ktb.zura.needu.friend.repository.FriendRepository;
import kr.ktb.zura.needu.notification.service.NotificationCreationService;
import kr.ktb.zura.needu.notification.service.NotificationStage;
import kr.ktb.zura.needu.notification.service.NotificationStageTimer;
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
    private final NotificationStageTimer stageTimer;
    private final int reminderDays;
    private final ZoneId notificationZone;

    public FriendBirthdayNotificationService(
            FriendRepository friendRepository,
            NotificationCreationService notificationCreationService,
            NotificationStageTimer stageTimer,
            @Value("${needu.notification.friend-birthday.reminder-days}") int reminderDays,
            @Value("${needu.notification.friend-birthday.zone}") String notificationZone
    ) {
        this.friendRepository = friendRepository;
        this.notificationCreationService = notificationCreationService;
        this.stageTimer = stageTimer;
        this.reminderDays = reminderDays;
        this.notificationZone = ZoneId.of(notificationZone);
    }

    public FriendBirthdayNotificationResult createNotifications(LocalDate executionDate) {
        long startedAt = System.nanoTime();
        // FRIEND_BIRTHDAY 알림은 이 배치만 만들므로 전후 차이가 이번 실행의 단계별 시간이다
        Map<NotificationStage, Duration> stageTotalsBefore = stageTimer.snapshot(NotificationType.FRIEND_BIRTHDAY);

        LocalDate birthdayDate = executionDate.plusDays(reminderDays);
        boolean includeLeapDay = !birthdayDate.isLeapYear()
                && birthdayDate.getMonthValue() == 3
                && birthdayDate.getDayOfMonth() == 1;
        // shortcut: 하루 대상 관계를 한 번에 조회한다, 메모리 부담이 커지면 keyset batch로 전환
        List<FriendBirthdayNotificationTarget> targets = friendRepository.findAllBirthdayNotificationTargets(
                birthdayDate.getMonthValue(), birthdayDate.getDayOfMonth(), includeLeapDay);
        long targetQueriedAt = System.nanoTime();

        Map<BirthdayFriend, Set<Long>> receiverUserIdsByFriend = targets.stream()
                .collect(Collectors.groupingBy(
                        target -> new BirthdayFriend(target.friendUserId(), target.nickname()),
                        LinkedHashMap::new,
                        Collectors.mapping(FriendBirthdayNotificationTarget::ownerUserId, Collectors.toSet())
                ));

        LocalDateTime expiresAt = birthdayDate.plusDays(1)
                .atStartOfDay(notificationZone)
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();

        long createStartedAt = System.nanoTime();
        int createdNotificationCount = 0;
        for (Map.Entry<BirthdayFriend, Set<Long>> entry : receiverUserIdsByFriend.entrySet()) {
            BirthdayFriend friend = entry.getKey();
            try {
                createdNotificationCount += notificationCreationService.createNotifications(
                        entry.getValue(),
                        NotificationType.FRIEND_BIRTHDAY,
                        null,
                        friend.nickname() + " 님의 생일이 " + reminderDays + "일 남았어요.",
                        BODY,
                        NotificationResourceType.USER,
                        friend.userId(),
                        "friend-birthday:" + birthdayDate + ":" + friend.userId() + ":d-" + reminderDays,
                        expiresAt
                ).size();
            }catch (Exception e) {
                log.error("Friend birthday notification creation failed. birthdayDate={}, friendUserId={}",
                        birthdayDate, friend.userId(), e);
            }
        }
        long finishedAt = System.nanoTime();

        Map<NotificationStage, Long> stageMillis = stageMillisSince(stageTotalsBefore);
        long createMillis = Duration.ofNanos(finishedAt - createStartedAt).toMillis();
        FriendBirthdayNotificationResult result = new FriendBirthdayNotificationResult(
                executionDate,
                birthdayDate,
                receiverUserIdsByFriend.size(),
                targets.size(),
                createdNotificationCount,
                Duration.ofNanos(finishedAt - startedAt).toMillis(),
                Duration.ofNanos(targetQueriedAt - startedAt).toMillis(),
                createMillis,
                stageMillis,
                createMillis - stageMillis.values().stream().mapToLong(Long::longValue).sum()
        );
        log.info("Friend birthday notifications created. result={}", result);
        return result;
    }

    private Map<NotificationStage, Long> stageMillisSince(Map<NotificationStage, Duration> totalsBefore) {
        Map<NotificationStage, Duration> totalsAfter = stageTimer.snapshot(NotificationType.FRIEND_BIRTHDAY);
        Map<NotificationStage, Long> stageMillis = new EnumMap<>(NotificationStage.class);
        totalsAfter.forEach((stage, total) ->
                stageMillis.put(stage, total.minus(totalsBefore.get(stage)).toMillis()));
        return stageMillis;
    }

    private record BirthdayFriend(Long userId, String nickname) {
    }
}
