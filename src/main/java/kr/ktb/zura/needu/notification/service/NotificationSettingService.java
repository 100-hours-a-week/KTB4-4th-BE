package kr.ktb.zura.needu.notification.service;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import kr.ktb.zura.needu.notification.dto.request.UpdateNotificationSettingRequest;
import kr.ktb.zura.needu.notification.dto.response.NotificationSettingResponse;
import kr.ktb.zura.needu.notification.dto.response.NotificationSettingsResponse;
import kr.ktb.zura.needu.notification.entity.NotificationSetting;
import kr.ktb.zura.needu.notification.repository.NotificationSettingRepository;
import kr.ktb.zura.needu.notification.type.NotificationSettingType;
import kr.ktb.zura.needu.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationSettingService {

    private final NotificationSettingRepository notificationSettingRepository;
    private final UserService userService;

    public NotificationSettingsResponse findAllNotificationSettings(Long userId) {
        userService.validateActiveUser(userId);
        Map<NotificationSettingType, NotificationSetting> settings = new EnumMap<>(NotificationSettingType.class);
        notificationSettingRepository.findAllByUserId(userId)
                .forEach(setting -> settings.put(setting.getType(), setting));

        return new NotificationSettingsResponse(Arrays.stream(NotificationSettingType.values())
                .map(type -> NotificationSettingResponse.from(type, settings.get(type)))
                .toList());
    }

    @Transactional
    public NotificationSettingResponse updateNotificationSetting(
            Long userId, NotificationSettingType type, UpdateNotificationSettingRequest request) {
        userService.validateActiveUser(userId);
        NotificationSetting setting = notificationSettingRepository.findByUserIdAndType(userId, type)
                .orElseGet(() -> NotificationSetting.create(userId, type, type.isDefaultEnabled()));
        setting.update(request.enabled());
        return NotificationSettingResponse.from(type, notificationSettingRepository.saveAndFlush(setting));
    }
}
