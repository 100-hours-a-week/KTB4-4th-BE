package kr.ktb.zura.needu.notification.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import kr.ktb.zura.needu.notification.entity.NotificationSetting;
import kr.ktb.zura.needu.notification.type.NotificationSettingType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationSettingRepository extends JpaRepository<NotificationSetting, Long> {

    @Modifying
    @Query("delete from NotificationSetting setting where setting.userId = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);

    List<NotificationSetting> findAllByUserId(Long userId);

    List<NotificationSetting> findAllByUserIdInAndType(
            Collection<Long> userIds,
            NotificationSettingType type
    );

    Optional<NotificationSetting> findByUserIdAndType(Long userId, NotificationSettingType type);
}
