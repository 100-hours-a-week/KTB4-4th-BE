package kr.ktb.zura.needu.notification.entity;

import static org.assertj.core.api.Assertions.assertThat;

import kr.ktb.zura.needu.notification.type.NotificationCategory;
import kr.ktb.zura.needu.notification.type.NotificationResourceType;
import kr.ktb.zura.needu.notification.type.NotificationType;
import org.junit.jupiter.api.Test;

class NotificationEventTest {

    @Test
    void create_friendBirthday_mapsEventCategory() {
        NotificationEvent event = NotificationEvent.create(
                NotificationType.FRIEND_BIRTHDAY,
                null,
                "친구의 생일이에요.",
                "선물을 준비해 보세요.",
                NotificationResourceType.USER,
                2L,
                "friend-birthday:2:2026-10-08"
        );

        assertThat(event.getCategory()).isEqualTo(NotificationCategory.EVENT);
        assertThat(event.getActorUserId()).isNull();
        assertThat(event.getResourceType()).isEqualTo(NotificationResourceType.USER);
        assertThat(event.getResourceId()).isEqualTo(2L);
    }
}
