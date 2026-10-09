package kr.ktb.zura.needu.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Optional;
import kr.ktb.zura.needu.notification.dto.request.UpdateNotificationSettingRequest;
import kr.ktb.zura.needu.notification.dto.response.NotificationSettingResponse;
import kr.ktb.zura.needu.notification.dto.response.NotificationSettingsResponse;
import kr.ktb.zura.needu.notification.entity.NotificationSetting;
import kr.ktb.zura.needu.notification.repository.NotificationSettingRepository;
import kr.ktb.zura.needu.notification.type.NotificationSettingType;
import org.junit.jupiter.api.Test;

class NotificationSettingServiceTest {

    private static final Long USER_ID = 1L;

    private final NotificationSettingRepository notificationSettingRepository =
            mock(NotificationSettingRepository.class);
    private final NotificationSettingService notificationSettingService =
            new NotificationSettingService(notificationSettingRepository);

    @Test
    void someSettingsMissing_findAllNotificationSettings_usesDefaults() {
        NotificationSetting marketing = NotificationSetting.create(
                USER_ID, NotificationSettingType.MARKETING, true);
        given(notificationSettingRepository.findAllByUserId(USER_ID)).willReturn(List.of(marketing));

        NotificationSettingsResponse response =
                notificationSettingService.findAllNotificationSettings(USER_ID);

        assertThat(response.settings()).containsExactly(
                new NotificationSettingResponse(NotificationSettingType.FRIEND_JOINED, true, null),
                new NotificationSettingResponse(NotificationSettingType.FRIEND_BIRTHDAY, true, null),
                new NotificationSettingResponse(NotificationSettingType.ANNIVERSARY_EVENT, true, null),
                new NotificationSettingResponse(NotificationSettingType.MARKETING, true, null)
        );
    }

    @Test
    void settingMissing_updateNotificationSetting_createsOverride() {
        NotificationSetting saved = NotificationSetting.create(
                USER_ID, NotificationSettingType.MARKETING, true);
        given(notificationSettingRepository.findByUserIdAndType(
                USER_ID, NotificationSettingType.MARKETING)).willReturn(Optional.empty());
        given(notificationSettingRepository.saveAndFlush(org.mockito.ArgumentMatchers.any(NotificationSetting.class)))
                .willReturn(saved);

        NotificationSettingResponse response = notificationSettingService.updateNotificationSetting(
                USER_ID, NotificationSettingType.MARKETING, new UpdateNotificationSettingRequest(true));

        assertThat(response).isEqualTo(
                new NotificationSettingResponse(NotificationSettingType.MARKETING, true, null));
        verify(notificationSettingRepository).saveAndFlush(org.mockito.ArgumentMatchers.argThat(setting ->
                setting.getUserId().equals(USER_ID)
                        && setting.getType() == NotificationSettingType.MARKETING
                        && setting.isEnabled()));
    }
}
