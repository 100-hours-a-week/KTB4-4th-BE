package kr.ktb.zura.needu.product.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import static lombok.AccessLevel.PROTECTED;

@Getter
@Entity
@Table(
        name = "gift_recommendations",
        indexes = @Index(
                name = "idx_gift_recommendations_user_id_score_id",
                columnList = "user_id, score, id"
        )
)
@NoArgsConstructor(access = PROTECTED)
public class GiftProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId; //user 엔티티 연결 필요

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, precision = 8, scale = 6)
    private BigDecimal score;

    @Column(length = 500)
    private String reason;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> tasteKeywords;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public GiftProduct(Long userId, Product product, BigDecimal score, String reason, List<String> tasteKeywords) {
        this.userId = userId;
        this.product = product;
        this.score = score;
        this.reason = reason;
        this.tasteKeywords = tasteKeywords;
    }

    public void updateScore(BigDecimal score) {
        this.score = score;
    }
}
