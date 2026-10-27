package kr.ktb.zura.needu.anonymouschat.entity;

import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import static lombok.AccessLevel.PROTECTED;

@Getter
@Entity
@Table(name = "anonymous_rooms",
        indexes = {
                @Index(name = "idx_anonymous_rooms_questioner_activity",
                        columnList = "questioner_user_id, last_activity_at DESC, id DESC"),
                @Index(name = "idx_anonymous_rooms_recipient_activity",
                        columnList = "recipient_user_id, last_activity_at DESC, id DESC")
        },
        check = @CheckConstraint(name = "ck_anonymous_rooms_distinct_users",
                constraint = "questioner_user_id <> recipient_user_id")
)
@NoArgsConstructor(access = PROTECTED)
public class AnonymousRoom {

    public static final int QUESTIONER_ALIAS_MAX_LENGTH = 20;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private Long questionerUserId;

    @Column(nullable = false)
    private Long recipientUserId;

    @Column(nullable = false, length = QUESTIONER_ALIAS_MAX_LENGTH)
    private String questionerAlias;

    private Long questionerLastReadMessageId;

    private Long recipientLastReadMessageId;

    @CreationTimestamp
    @Column(nullable = false)
    private LocalDateTime lastActivityAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 질문받은 사용자가 방을 최초로 확인한 시각
    private LocalDateTime openedAt;

    private LocalDateTime expiresAt;

    public AnonymousRoom(Long productId, Long questionerUserId, Long recipientUserId, String questionerAlias) {
        this.productId = productId;
        this.questionerUserId = questionerUserId;
        this.recipientUserId = recipientUserId;
        this.questionerAlias = questionerAlias;
    }

    public boolean isQuestioner(Long userId) {
        return questionerUserId.equals(userId);
    }

    public boolean isParticipant(Long userId) {
        return isQuestioner(userId) || recipientUserId.equals(userId);
    }
}
