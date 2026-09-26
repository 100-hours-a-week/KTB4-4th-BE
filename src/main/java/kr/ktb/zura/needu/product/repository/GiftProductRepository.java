package kr.ktb.zura.needu.product.repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

import kr.ktb.zura.needu.product.entity.GiftProduct;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GiftProductRepository extends JpaRepository<GiftProduct, Long> {

    @Query("""
            select gp
            from GiftProduct gp
            join fetch gp.product p
            where gp.userId = :userId
              and p.status = kr.ktb.zura.needu.product.type.ProductStatus.ACTIVE
              and p.deletedAt is null
              and p.price between :minPrice and :maxPrice
            order by gp.score desc, gp.id desc
            """)
    List<GiftProduct> findAllByUserIdAndPriceRange(
            @Param("userId") Long userId,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            Limit limit
    );

    @Query("""
            select gp
            from GiftProduct gp
            join fetch gp.product p
            where gp.userId = :userId
              and p.status = kr.ktb.zura.needu.product.type.ProductStatus.ACTIVE
              and p.deletedAt is null
              and p.price between :minPrice and :maxPrice
              and (gp.score < :score or (gp.score = :score and gp.id < :id))
            order by gp.score desc, gp.id desc
            """)
    List<GiftProduct> findAllByUserIdAndPriceRangeAfterCursor(
            @Param("userId") Long userId,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            @Param("score") BigDecimal score,
            @Param("id") Long id,
            Limit limit
    );

    List<GiftProduct> findAllByUserIdAndProductIdIn(Long userId, Collection<Long> productIds);
}
