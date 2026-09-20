package kr.ktb.zura.needu.aichat.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.LocalDateTime;

import kr.ktb.zura.needu.aichat.type.AiChatRoomStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import static lombok.AccessLevel.PROTECTED;

@Getter
@Entity
@Table(
        name = "ai_chat_rooms",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ai_chat_rooms_active_user_id",
                columnNames = "active_user_id"
        )
)
@NoArgsConstructor(access = PROTECTED)
public class AiChatRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    // 진행 중(PENDING/ACTIVE/ANALYZING)인 방에만 userId를 채워 유일 제약으로 사용자당 진행 중인 방을 하나로 제한
    private Long activeUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AiChatRoomStatus status = AiChatRoomStatus.PENDING;

    // AI 세션 만료 예상 시각이자 대화방과 대화 원문이 함께 삭제될 예정 시각
    private LocalDateTime purgeAt;

    @Version
    private Long version;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private LocalDateTime deletedAt;

    private AiChatRoom(Long userId) {
        this.userId = userId;
        this.activeUserId = userId;
    }

    // AI 세션 생성 요청에 대화방 ID가 필요 => AI 호출 전에 PENDING 상태로 먼저 저장
    public static AiChatRoom reserve(Long userId) {
        return new AiChatRoom(userId);
    }

    public void activate(LocalDateTime purgeAt) {
        this.status = AiChatRoomStatus.ACTIVE;
        this.purgeAt = purgeAt;
    }

    public void expire() {
        this.status = AiChatRoomStatus.EXPIRED;
        this.activeUserId = null;
    }

    public void discard(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
        this.activeUserId = null;
    }

    public boolean isPending() {
        return status == AiChatRoomStatus.PENDING;
    }

    public boolean isActive() {
        return status == AiChatRoomStatus.ACTIVE;
    }

    public boolean isExpiredAt(LocalDateTime now) {
        return purgeAt != null && !purgeAt.isAfter(now);
    }

    public boolean isReservedBefore(LocalDateTime threshold) {
        return createdAt.isBefore(threshold);
    }
}
