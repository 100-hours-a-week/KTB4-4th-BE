package kr.ktb.zura.needu.anonymouschat.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import static lombok.AccessLevel.PROTECTED;

@Getter
@Entity
@Table(name = "anonymous_messages",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_anonymous_messages_room_sender_client_message",
                columnNames = {"room_id", "sender_user_id", "client_message_id"}
        ),
        indexes = @Index(name = "idx_anonymous_messages_room_id_id", columnList = "room_id, id")
)
@NoArgsConstructor(access = PROTECTED)
public class AnonymousMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_anonymous_messages_room"))
    private AnonymousRoom room;

    @Column(nullable = false)
    private Long senderUserId;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 36)
    private UUID clientMessageId;

    @Column(nullable = false, length = 1000)
    private String content;

    // 수신자가 이 메시지를 읽은 시각. NULL이면 미읽음
    private LocalDateTime readAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime deletedAt;

    public AnonymousMessage(AnonymousRoom room, Long senderUserId, UUID clientMessageId, String content) {
        this.room = room;
        this.senderUserId = senderUserId;
        this.clientMessageId = clientMessageId;
        this.content = content;
    }
}
