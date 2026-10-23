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
import kr.ktb.zura.needu.friend.dto.response.FriendBirthdayNotificationResult;
import kr.ktb.zura.needu.friend.dto.response.FriendBirthdayNotificationTarget;
import kr.ktb.zura.needu.friend.repository.FriendRepository;
import kr.ktb.zura.needu.notification.service.NotificationCreateRequest;
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
    private final int chunkSize;

    public FriendBirthdayNotificationService(
            FriendRepository friendRepository,
            NotificationCreationService notificationCreationService,
            @Value("${needu.notification.friend-birthday.reminder-days}") int reminderDays,
            @Value("${needu.notification.friend-birthday.zone}") String notificationZone,
            @Value("${needu.notification.friend-birthday.chunk-size}") int chunkSize
    ) {
        if (chunkSize < 1) {
            throw new IllegalArgumentException("Friend birthday notification chunk size must be positive.");
        }
        this.friendRepository = friendRepository;
        this.notificationCreationService = notificationCreationService;
        this.reminderDays = reminderDays;
        this.notificationZone = ZoneId.of(notificationZone);
        this.chunkSize = chunkSize;
    }

    public FriendBirthdayNotificationResult createNotifications(LocalDate executionDate) {
        LocalDate birthdayDate = executionDate.plusDays(reminderDays);
        boolean includeLeapDay = !birthdayDate.isLeapYear()
                && birthdayDate.getMonthValue() == 3
                && birthdayDate.getDayOfMonth() == 1;
        List<FriendBirthdayNotificationTarget> targets = friendRepository.findAllBirthdayNotificationTargets(
                birthdayDate.getMonthValue(), birthdayDate.getDayOfMonth(), includeLeapDay);

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

        List<NotificationCreateRequest> requests = receiverUserIdsByFriend.entrySet().stream()
                .map(entry -> new NotificationCreateRequest(
                        entry.getValue(),
                        null,
                        entry.getKey().nickname() + " 님의 생일이 " + reminderDays + "일 남았어요.",
                        BODY,
                        NotificationResourceType.USER,
                        entry.getKey().userId(),
                        "friend-birthday:" + birthdayDate + ":" + entry.getKey().userId() + ":d-" + reminderDays,
                        expiresAt
                ))
                .toList();

        int createdNotificationCount = 0;
        for (int from = 0; from < requests.size(); from += chunkSize) {
            createdNotificationCount += createChunk(
                    requests.subList(from, Math.min(from + chunkSize, requests.size())), birthdayDate);
        }

        FriendBirthdayNotificationResult result = new FriendBirthdayNotificationResult(
                executionDate,
                birthdayDate,
                receiverUserIdsByFriend.size(),
                targets.size(),
                createdNotificationCount
        );
        log.info("Friend birthday notifications created. result={}", result);
        return result;
    }

    // 묶음이 실패하면 롤백된 묶음만 한 명씩 다시 만들어, 실패한 생일자 외에는 알림이 빠지지 않게 한다
    private int createChunk(List<NotificationCreateRequest> chunk, LocalDate birthdayDate) {
        try {
            return notificationCreationService.createNotificationsInBatch(NotificationType.FRIEND_BIRTHDAY, chunk)
                    .size();
        } catch (RuntimeException exception) {
            log.warn("Friend birthday notification chunk failed. Retrying one by one. birthdayDate={}, chunkSize={}",
                    birthdayDate, chunk.size(), exception);
        }

        int createdNotificationCount = 0;
        for (NotificationCreateRequest request : chunk) {
            try {
                createdNotificationCount += notificationCreationService.createNotifications(
                        request.receiverUserIds(),
                        NotificationType.FRIEND_BIRTHDAY,
                        request.actorUserId(),
                        request.title(),
                        request.body(),
                        request.resourceType(),
                        request.resourceId(),
                        request.dedupKey(),
                        request.expiresAt()
                ).size();
            } catch (RuntimeException exception) {
                log.error("Friend birthday notification creation failed. birthdayDate={}, friendUserId={}",
                        birthdayDate, request.resourceId(), exception);
            }
        }
        return createdNotificationCount;
    }

    private record BirthdayFriend(Long userId, String nickname) {
    }
}
