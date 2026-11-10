package kr.ktb.zura.needu.product.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import kr.ktb.zura.needu.product.entity.PurchaseCheck;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PurchaseCheckRepository extends JpaRepository<PurchaseCheck, Long> {

    @Modifying
    @Query("""
            delete from PurchaseCheck pc
            where pc.userId = :userId
               or pc.productLinkClick.id in (
                   select plc.id
                   from ProductLinkClick plc
                   where plc.friendUserId = :userId
               )
            """)
    void deleteAllRelatedToUserId(@Param("userId") Long userId);

    Optional<PurchaseCheck> findByIdAndUserId(Long id, Long userId);

    @Query("""
            select pc
            from PurchaseCheck pc
            join fetch pc.productLinkClick plc
            join fetch plc.product
            where pc.id = :id
              and pc.userId = :userId
            """)
    Optional<PurchaseCheck> findWithProductByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    @Query("""
            select pc
            from PurchaseCheck pc
            join fetch pc.productLinkClick plc
            join fetch plc.product
            where pc.id in :ids
              and pc.userId = :userId
            """)
    List<PurchaseCheck> findAllWithProductByIdInAndUserId(
            @Param("ids") Collection<Long> ids, @Param("userId") Long userId);

    @Query("""
            select new kr.ktb.zura.needu.product.repository.PurchaseActivity(
                p.platformType, p.externalId, plc.context, pc.answeredAt)
            from PurchaseCheck pc
            join pc.productLinkClick plc
            join plc.product p
            where pc.purchased = true
              and pc.answeredAt >= :since
              and plc.deletedAt is null
              and ((pc.userId = :userId
                    and plc.context <> kr.ktb.zura.needu.product.type.ProductContext.FRIEND_GIFT)
                or (plc.friendUserId = :userId
                    and plc.context = kr.ktb.zura.needu.product.type.ProductContext.FRIEND_GIFT))
            order by pc.answeredAt desc, pc.id desc
            """)
    List<PurchaseActivity> findAllPurchasedActivitiesRelatedToUserIdSince(
            @Param("userId") Long userId, @Param("since") LocalDateTime since, Limit limit);
}
