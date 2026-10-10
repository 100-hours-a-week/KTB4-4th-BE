package kr.ktb.zura.needu.product.entity;

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

import kr.ktb.zura.needu.product.type.ProductContext;
import kr.ktb.zura.needu.product.type.ProductFeedbackType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import static lombok.AccessLevel.PROTECTED;

@Getter
@Entity
@Table(
        name = "product_feedbacks",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_product_feedbacks_user_product_context",
                columnNames = {"user_id", "product_id", "context"}
        )
)
@NoArgsConstructor(access = PROTECTED)
public class ProductFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductContext context;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductFeedbackType feedbackType;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private LocalDateTime deletedAt;

    public ProductFeedback(Long userId, Product product, ProductContext context, ProductFeedbackType feedbackType) {
        this.userId = userId;
        this.product = product;
        this.context = context;
        this.feedbackType = feedbackType;
    }

    public void updateFeedback(ProductFeedbackType feedbackType) {
        this.feedbackType = feedbackType;
        this.deletedAt = null;
    }

    public void delete() {
        if (!isDeleted()) {
            deletedAt = LocalDateTime.now();
        }
    }

    public boolean isDisliked() {
        return feedbackType == ProductFeedbackType.DISLIKE;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
