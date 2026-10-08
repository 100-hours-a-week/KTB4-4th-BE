package kr.ktb.zura.needu.notification.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NotificationSettingTest {

    @Test
    void update_nullFields_keepsExistingValues() {
        NotificationSetting setting = NotificationSetting.createDefault(1L);

        setting.update(null, false, null, true);

        assertThat(setting.isFriendJoined()).isTrue();
        assertThat(setting.isFriendBirthday()).isFalse();
        assertThat(setting.isAnniversaryEvent()).isTrue();
        assertThat(setting.isMarketing()).isTrue();
    }
}
