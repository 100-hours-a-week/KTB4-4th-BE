package kr.ktb.zura.needu.product.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import static lombok.AccessLevel.PROTECTED;

@Getter
@Entity
@Table(
        name = "purchase_checks",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_purchase_checks_product_link_click_id",
                columnNames = "product_link_click_id"
        )
)
@NoArgsConstructor(access = PROTECTED)
public class PurchaseCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 소유자 조건 조회(findByIdAndUserId)를 위해 클릭의 userId를 함께 저장한다
    @Column(nullable = false)
    private Long userId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_link_click_id", nullable = false)
    private ProductLinkClick productLinkClick;

    // 답하기 전에는 null
    private Boolean purchased;

    private LocalDateTime answeredAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // TODO: PURCHASE_CHECK_ALREADY_ANSWERED(409) 에러 처리 필요 => 안하면 500 에러 발생 가능
    @Version
    private Long version;

    private PurchaseCheck(ProductLinkClick productLinkClick) {
        this.userId = productLinkClick.getUserId();
        this.productLinkClick = productLinkClick;
    }

    public static PurchaseCheck create(ProductLinkClick productLinkClick) {
        return new PurchaseCheck(productLinkClick);
    }

    public void answer(boolean purchased) {
        this.purchased = purchased;
        this.answeredAt = LocalDateTime.now();
    }

    public boolean isAnswered() {
        return answeredAt != null;
    }
}
