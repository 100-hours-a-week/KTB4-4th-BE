package kr.ktb.zura.needu.product.repository;

import java.math.BigDecimal;
import java.util.List;

import kr.ktb.zura.needu.product.entity.PersonalProduct;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PersonalProductRepository extends JpaRepository<PersonalProduct, Long> {

    @Query("""
            select pp
            from PersonalProduct pp
            join fetch pp.product p
            where pp.userId = :userId
              and p.status = kr.ktb.zura.needu.product.type.ProductStatus.ACTIVE
              and p.deletedAt is null
            order by pp.score desc, pp.id desc
            """)
    List<PersonalProduct> findAllByUserId(@Param("userId") Long userId, Limit limit);

    @Query("""
            select pp
            from PersonalProduct pp
            join fetch pp.product p
            where pp.userId = :userId
              and p.status = kr.ktb.zura.needu.product.type.ProductStatus.ACTIVE
              and p.deletedAt is null
              and (pp.score < :score or (pp.score = :score and pp.id < :id))
            order by pp.score desc, pp.id desc
            """)
    List<PersonalProduct> findAllByUserIdAfterCursor(
            @Param("userId") Long userId,
            @Param("score") BigDecimal score,
            @Param("id") Long id,
            Limit limit
    );
}
