package kr.ktb.zura.needu.product.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import kr.ktb.zura.needu.product.entity.ProductFeedback;
import kr.ktb.zura.needu.product.type.ProductContext;
import kr.ktb.zura.needu.product.type.ProductFeedbackType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductFeedbackRepository extends JpaRepository<ProductFeedback, Long> {

    Optional<ProductFeedback> findByUserIdAndProductIdAndContext(Long userId, Long productId, ProductContext context);

    boolean existsByUserIdAndProductIdAndFeedbackType(Long userId, Long productId, ProductFeedbackType feedbackType);

    @Query("""
            select distinct pf.product.id
            from ProductFeedback pf
            where pf.userId = :userId
              and pf.product.id in :productIds
              and pf.feedbackType = kr.ktb.zura.needu.product.type.ProductFeedbackType.DISLIKE
            """)
    List<Long> findAllDislikedProductIds(
            @Param("userId") Long userId, @Param("productIds") Collection<Long> productIds);
}
