package kr.ktb.zura.needu.aichat.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.UUID;

import kr.ktb.zura.needu.aichat.type.SenderType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import static lombok.AccessLevel.PROTECTED;

@Getter
@Entity
@Table(
        name = "ai_messages",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ai_messages_room_client_message_id",
                columnNames = {"ai_chat_room_id", "client_message_id"}
        )
)
@NoArgsConstructor(access = PROTECTED)
public class AiMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ai_chat_room_id", nullable = false)
    private AiChatRoom aiChatRoom;

    // 클라이언트가 생성한 중복 방지용 식별자. AI가 보낸 메시지(인사말, 답변)에는 없다
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 36)
    private UUID clientMessageId;

    // AI 답변이 어떤 사용자 메시지에 대한 것인지. 사용자 메시지와 인사말에는 없다
    private Long replyToMessageId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SenderType senderType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime deletedAt;

    private AiMessage(AiChatRoom aiChatRoom, SenderType senderType, UUID clientMessageId,
                      Long replyToMessageId, String content) {
        this.aiChatRoom = aiChatRoom;
        this.senderType = senderType;
        this.clientMessageId = clientMessageId;
        this.replyToMessageId = replyToMessageId;
        this.content = content;
    }

    public static AiMessage createGreeting(AiChatRoom aiChatRoom, String content) {
        return new AiMessage(aiChatRoom, SenderType.AI, null, null, content);
    }

    public static AiMessage createUserMessage(AiChatRoom aiChatRoom, UUID clientMessageId, String content) {
        return new AiMessage(aiChatRoom, SenderType.USER, clientMessageId, null, content);
    }

    public static AiMessage createReply(AiChatRoom aiChatRoom, Long replyToMessageId, String content) {
        return new AiMessage(aiChatRoom, SenderType.AI, null, replyToMessageId, content);
    }
}
