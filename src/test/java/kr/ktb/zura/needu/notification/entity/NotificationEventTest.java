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

    @Test
    void actorWithdraws_anonymizeActor_removesActorAndReplacesNickname() {
        NotificationEvent event = NotificationEvent.create(
                NotificationType.POKE,
                1L,
                "니듀님이 콕 찔렀어요.",
                "니듀님과 대화를 시작해 보세요.",
                NotificationResourceType.AI_CONVERSATION,
                null,
                "poke:1:request-id"
        );

        event.anonymizeActor("니듀");

        assertThat(event.getActorUserId()).isNull();
        assertThat(event.getTitle()).isEqualTo("탈퇴한 사용자님이 콕 찔렀어요.");
        assertThat(event.getBody()).isEqualTo("탈퇴한 사용자님과 대화를 시작해 보세요.");
    }
}
