package kr.ktb.zura.needu.aichat.entity;

import jakarta.persistence.*;

import java.time.Instant;
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
@Table(name = "ai_messages")
@NoArgsConstructor(access = PROTECTED)
public class AiMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ai_chat_room_id", nullable = false)
    private AiChatRoom aiChatRoom;


    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 36)
    private UUID clientMessageId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SenderType senderType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant deletedAt;
}
