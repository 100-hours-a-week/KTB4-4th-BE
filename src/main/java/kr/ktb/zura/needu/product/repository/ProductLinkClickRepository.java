package kr.ktb.zura.needu.product.repository;

import java.time.LocalDateTime;
import java.util.List;

import kr.ktb.zura.needu.product.entity.ProductLinkClick;
import kr.ktb.zura.needu.product.type.ProductContext;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductLinkClickRepository extends JpaRepository<ProductLinkClick, Long> {

    @Modifying
    @Query("delete from ProductLinkClick click where click.userId = :userId or click.friendUserId = :userId")
    void deleteAllRelatedToUserId(@Param("userId") Long userId);

    @Query("""
            select count(plc) > 0
            from ProductLinkClick plc
            where plc.userId = :userId
              and plc.product.id = :productId
              and plc.context = :context
              and plc.createdAt > :since
              and plc.deletedAt is null
            """)
    boolean existsRecentClick(
            @Param("userId") Long userId,
            @Param("productId") Long productId,
            @Param("context") ProductContext context,
            @Param("since") LocalDateTime since
    );

    @Query("""
            select plc.id
            from ProductLinkClick plc
            where plc.createdAt > :from
              and plc.createdAt <= :to
              and plc.id > :lastId
              and plc.deletedAt is null
              and not exists (
                  select pc.id
                  from PurchaseCheck pc
                  where pc.productLinkClick.id = plc.id
              )
            order by plc.id
            """)
    List<Long> findPurchaseCheckTargetIds(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("lastId") Long lastId,
            Limit limit
    );
}
