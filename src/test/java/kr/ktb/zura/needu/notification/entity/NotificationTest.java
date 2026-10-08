package kr.ktb.zura.needu.notification.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import kr.ktb.zura.needu.notification.type.NotificationCategory;
import kr.ktb.zura.needu.notification.type.NotificationTargetType;
import org.junit.jupiter.api.Test;

class NotificationTest {

    @Test
    void readAndDelete_calledTwice_keepsInitialTimestamps() {
        Notification notification = Notification.create(
                1L,
                NotificationCategory.POKE,
                "title",
                "body",
                NotificationTargetType.FRIEND_DETAIL,
                "2",
                "poke:1:2"
        );

        notification.read();
        notification.delete();
        LocalDateTime readAt = notification.getReadAt();
        LocalDateTime deletedAt = notification.getDeletedAt();

        notification.read();
        notification.delete();

        assertThat(notification.isRead()).isTrue();
        assertThat(notification.isDeleted()).isTrue();
        assertThat(notification.getReadAt()).isEqualTo(readAt);
        assertThat(notification.getDeletedAt()).isEqualTo(deletedAt);
    }
}
