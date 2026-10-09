package kr.ktb.zura.needu.notification.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import kr.ktb.zura.needu.notification.entity.NotificationSetting;
import kr.ktb.zura.needu.notification.type.NotificationSettingType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationSettingRepository extends JpaRepository<NotificationSetting, Long> {

    List<NotificationSetting> findAllByUserId(Long userId);

    List<NotificationSetting> findAllByUserIdInAndType(
            Collection<Long> userIds,
            NotificationSettingType type
    );

    Optional<NotificationSetting> findByUserIdAndType(Long userId, NotificationSettingType type);
}
