package kr.ktb.zura.needu.product.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

import kr.ktb.zura.needu.product.type.ProductContext;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import static lombok.AccessLevel.PROTECTED;

@Getter
@Entity
@Table(
        name = "product_link_clicks",
        indexes = {
                // 구매 확인 스케줄러가 일정 시간이 지난 클릭을 시간순으로 훑음
                @Index(name = "idx_product_link_clicks_created_at", columnList = "created_at"),
                // 최근 7일 안에 동일 클릭이 있으면 새로 저장하지 않음
                @Index(
                        name = "idx_product_link_clicks_user_product_context_created_at",
                        columnList = "user_id, product_id, context, created_at"
                )
        }
)
@NoArgsConstructor(access = PROTECTED)
public class ProductLinkClick {

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

    // FRIEND_GIFT일 때만 존재
    private Long friendUserId;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private LocalDateTime deletedAt;

    public ProductLinkClick(Long userId, Product product, ProductContext context,
                            Long friendUserId) {
        this.userId = userId;
        this.product = product;
        this.context = context;
        this.friendUserId = friendUserId;
    }

    public void delete() {
        if (!isDeleted()) {
            deletedAt = LocalDateTime.now();
        }
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
