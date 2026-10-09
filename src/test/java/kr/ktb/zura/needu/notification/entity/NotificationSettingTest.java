package kr.ktb.zura.needu.notification.entity;

import static org.assertj.core.api.Assertions.assertThat;

import kr.ktb.zura.needu.notification.type.NotificationSettingType;
import org.junit.jupiter.api.Test;

class NotificationSettingTest {

    @Test
    void disabledSetting_update_enablesSetting() {
        NotificationSetting setting = NotificationSetting.create(
                1L, NotificationSettingType.MARKETING, false);

        setting.update(true);

        assertThat(setting.isEnabled()).isTrue();
    }
}
