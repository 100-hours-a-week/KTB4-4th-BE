package kr.ktb.zura.needu.product.service;

import java.math.BigDecimal;

import kr.ktb.zura.needu.product.entity.Product;
import kr.ktb.zura.needu.product.repository.ProductRepository;
import kr.ktb.zura.needu.product.type.PlatformType;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ai.server.mock-enabled", havingValue = "true")
public class MockProductDataInitializer implements ApplicationRunner {

    private static final String SELF_PRODUCT_ID = "88213";
    private static final String GIFT_PRODUCT_ID = "88214";

    private final ProductRepository productRepository;

    @Override
    public void run(ApplicationArguments args) {
        createIfMissing(SELF_PRODUCT_ID, "목 캠핑 테이블", "129000");
        createIfMissing(GIFT_PRODUCT_ID, "목 캠핑 머그컵", "24000");
    }

    private void createIfMissing(String externalId, String name, String price) {
        if (productRepository.findByPlatformTypeAndExternalId(PlatformType.COUPANG, externalId).isPresent()) {
            return;
        }
        productRepository.save(new Product(
                PlatformType.COUPANG,
                externalId,
                name,
                "CAMPING",
                "AI 채팅 연동 테스트용 목 상품",
                new BigDecimal(price),
                null,
                null,
                "https://example.com/products/" + externalId));
    }
}
