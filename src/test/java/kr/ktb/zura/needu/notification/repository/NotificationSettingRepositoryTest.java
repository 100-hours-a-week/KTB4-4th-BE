package kr.ktb.zura.needu.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import kr.ktb.zura.needu.notification.entity.NotificationSetting;
import kr.ktb.zura.needu.notification.type.NotificationSettingType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
class NotificationSettingRepositoryTest {

    @Autowired
    private NotificationSettingRepository notificationSettingRepository;

    @Test
    void settingsExist_findAllByUserId_returnsOnlyUserSettings() {
        notificationSettingRepository.save(NotificationSetting.create(
                1L, NotificationSettingType.FRIEND_JOINED, false));
        notificationSettingRepository.save(NotificationSetting.create(
                2L, NotificationSettingType.MARKETING, true));

        assertThat(notificationSettingRepository.findAllByUserId(1L))
                .extracting(NotificationSetting::getType)
                .containsExactly(NotificationSettingType.FRIEND_JOINED);
    }

    @Test
    void mixedUserSettings_findAllByUserIdInAndType_returnsOnlyRequestedType() {
        notificationSettingRepository.save(NotificationSetting.create(
                1L, NotificationSettingType.CHAT, true));
        notificationSettingRepository.save(NotificationSetting.create(
                2L, NotificationSettingType.CHAT, false));
        notificationSettingRepository.save(NotificationSetting.create(
                2L, NotificationSettingType.POKE, true));

        assertThat(notificationSettingRepository.findAllByUserIdInAndType(
                Set.of(1L, 2L), NotificationSettingType.CHAT))
                .extracting(NotificationSetting::getUserId)
                .containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void sameUserAndType_save_violatesUniqueConstraint() {
        notificationSettingRepository.saveAndFlush(NotificationSetting.create(
                1L, NotificationSettingType.MARKETING, true));

        assertThatThrownBy(() -> notificationSettingRepository.saveAndFlush(NotificationSetting.create(
                1L, NotificationSettingType.MARKETING, false)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
