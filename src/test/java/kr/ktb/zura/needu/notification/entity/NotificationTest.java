package kr.ktb.zura.needu.notification.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import kr.ktb.zura.needu.notification.type.NotificationResourceType;
import kr.ktb.zura.needu.notification.type.NotificationType;
import org.junit.jupiter.api.Test;

class NotificationTest {

    @Test
    void readAndDelete_calledTwice_keepsInitialTimestamps() {
        NotificationEvent event = NotificationEvent.create(
                NotificationType.POKE,
                1L,
                "title",
                "body",
                NotificationResourceType.USER,
                2L,
                "poke:1:2"
        );
        Notification notification = Notification.create(event, 2L, LocalDateTime.now().plusDays(30));

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
