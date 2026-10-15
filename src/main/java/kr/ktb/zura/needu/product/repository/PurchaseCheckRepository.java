package kr.ktb.zura.needu.product.repository;

import java.util.Optional;

import kr.ktb.zura.needu.product.entity.PurchaseCheck;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseCheckRepository extends JpaRepository<PurchaseCheck, Long> {

    Optional<PurchaseCheck> findByIdAndUserId(Long id, Long userId);
}
