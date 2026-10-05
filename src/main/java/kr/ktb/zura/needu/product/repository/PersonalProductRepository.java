package kr.ktb.zura.needu.product.repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

import kr.ktb.zura.needu.product.entity.PersonalProduct;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PersonalProductRepository extends JpaRepository<PersonalProduct, Long> {

    @Query("""
            select new kr.ktb.zura.needu.product.repository.ProductPriceRange(min(p.price), max(p.price))
            from PersonalProduct pp
            join pp.product p
            where pp.userId = :userId
              and p.status = kr.ktb.zura.needu.product.type.ProductStatus.ACTIVE
              and p.deletedAt is null
            """)
    ProductPriceRange findPriceRangeByUserId(@Param("userId") Long userId);

    @Query("""
            select new kr.ktb.zura.needu.product.repository.PersonalProductSummary(
                pp.id, p.id, p.name, p.imageUrl, p.purchaseUrl, p.category, p.price,
                pp.score, pp.reason)
            from PersonalProduct pp
            join pp.product p
            where pp.userId = :userId
              and p.status = kr.ktb.zura.needu.product.type.ProductStatus.ACTIVE
              and p.deletedAt is null
              and p.price between :minPrice and :maxPrice
            order by pp.score desc, pp.id desc
            """)
    List<PersonalProductSummary> findAllByUserIdAndPriceRange(
            @Param("userId") Long userId,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            Limit limit
    );

    @Query("""
            select new kr.ktb.zura.needu.product.repository.PersonalProductSummary(
                pp.id, p.id, p.name, p.imageUrl, p.purchaseUrl, p.category, p.price,
                pp.score, pp.reason)
            from PersonalProduct pp
            join pp.product p
            where pp.userId = :userId
              and p.status = kr.ktb.zura.needu.product.type.ProductStatus.ACTIVE
              and p.deletedAt is null
              and (pp.score < :score or (pp.score = :score and pp.id < :id))
              and p.price between :minPrice and :maxPrice
            order by pp.score desc, pp.id desc
            """)
    List<PersonalProductSummary> findAllByUserIdAndPriceRangeAfterCursor(
            @Param("userId") Long userId,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            @Param("score") BigDecimal score,
            @Param("id") Long id,
            Limit limit
    );

    List<PersonalProduct> findAllByUserIdAndProductIdIn(Long userId, Collection<Long> productIds);
}
