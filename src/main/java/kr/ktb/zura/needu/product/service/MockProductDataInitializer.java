package kr.ktb.zura.needu.product.service;

import java.math.BigDecimal;

import kr.ktb.zura.needu.product.entity.Product;
import kr.ktb.zura.needu.product.repository.ProductRepository;
import kr.ktb.zura.needu.product.type.PlatformType;
import kr.ktb.zura.needu.product.type.ProductCategory;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ai.server.mock-enabled", havingValue = "true")
public class MockProductDataInitializer implements ApplicationRunner {

    private static final String SELF_PRODUCT_ID = "8255331367";
    private static final String GIFT_PRODUCT_ID = "6927419268";

    private final ProductRepository productRepository;

    @Override
    public void run(ApplicationArguments args) {
        createIfMissing(
                SELF_PRODUCT_ID,
                "코멧 아웃도어 휴대용 높이조절 캠핑스툴, 아몬드, 1개",
                ProductCategory.SPORTS,
                "5990",
                "https://thumbnail.coupangcdn.com/thumbnails/remote/230x230ex/image/retail/images/417649116842193-ea669531-ad28-4324-b741-236e6e140534.jpg",
                "https://www.coupang.com/vp/products/8255331367?itemId=26330885882&vendorItemId=93308154253");
        createIfMissing(
                GIFT_PRODUCT_ID,
                "비로르 스테인리스 304 캠핑 스텐 요리 핀셋 주방 고기 집게, 실버, 2개",
                ProductCategory.LIVING,
                "8900",
                "https://thumbnail.coupangcdn.com/thumbnails/remote/230x230ex/image/retail/images/242333577957777-03057161-de38-4a82-ac19-4d83edb28fdb.jpg",
                "https://www.coupang.com/vp/products/6927419268?itemId=16751060773&vendorItemId=88265784749");
    }

    private void createIfMissing(String externalId, String name, ProductCategory category, String price,
                                 String imageUrl, String purchaseUrl) {
        if (productRepository.findByPlatformTypeAndExternalId(PlatformType.COUPANG, externalId).isPresent()) {
            return;
        }
        productRepository.save(new Product(
                PlatformType.COUPANG,
                externalId,
                name,
                category,
                null,
                new BigDecimal(price),
                imageUrl,
                null,
                purchaseUrl));
    }
}
