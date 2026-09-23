package kr.ktb.zura.needu.product.repository;

import java.util.Optional;
import kr.ktb.zura.needu.product.entity.Product;
import kr.ktb.zura.needu.product.type.PlatformType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByPlatformTypeAndExternalId(PlatformType platformType, String externalId);
}
