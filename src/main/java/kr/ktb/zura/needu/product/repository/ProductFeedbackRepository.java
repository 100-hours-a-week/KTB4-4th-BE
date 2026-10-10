package kr.ktb.zura.needu.product.repository;

import java.util.Optional;

import kr.ktb.zura.needu.product.entity.ProductFeedback;
import kr.ktb.zura.needu.product.type.ProductContext;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductFeedbackRepository extends JpaRepository<ProductFeedback, Long> {

    Optional<ProductFeedback> findByUserIdAndProductIdAndContext(Long userId, Long productId, ProductContext context);
}
