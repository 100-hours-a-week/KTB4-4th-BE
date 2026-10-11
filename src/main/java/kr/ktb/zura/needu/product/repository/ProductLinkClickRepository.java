package kr.ktb.zura.needu.product.repository;

import java.time.LocalDateTime;

import kr.ktb.zura.needu.product.entity.ProductLinkClick;
import kr.ktb.zura.needu.product.type.ProductContext;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductLinkClickRepository extends JpaRepository<ProductLinkClick, Long> {

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
}
