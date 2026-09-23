package kr.ktb.zura.needu.product.service;

import java.util.List;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerRecommendationResult;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerRecommendedItem;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.product.entity.GiftProduct;
import kr.ktb.zura.needu.product.entity.PersonalProduct;
import kr.ktb.zura.needu.product.entity.Product;
import kr.ktb.zura.needu.product.exception.ProductErrorCode;
import kr.ktb.zura.needu.product.repository.GiftProductRepository;
import kr.ktb.zura.needu.product.repository.PersonalProductRepository;
import kr.ktb.zura.needu.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductRecommendationService {

    private final ProductRepository productRepository;
    private final PersonalProductRepository personalProductRepository;
    private final GiftProductRepository giftProductRepository;

    @Transactional
    public void saveRecommendations(
            Long userId,
            AiServerRecommendationResult self,
            AiServerRecommendationResult gift,
            List<String> tasteKeywords
    ) {
        List<PersonalProduct> personalProducts = toPersonalProducts(userId, self);
        List<GiftProduct> giftProducts = toGiftProducts(userId, gift, tasteKeywords);
        personalProductRepository.saveAll(personalProducts);
        giftProductRepository.saveAll(giftProducts);
    }

    private List<PersonalProduct> toPersonalProducts(Long userId, AiServerRecommendationResult recommendation) {
        return recommendation.items().stream()
                .map(item -> new PersonalProduct(
                        userId, findProduct(item), item.score(), item.reason()))
                .toList();
    }

    private List<GiftProduct> toGiftProducts(
            Long userId, AiServerRecommendationResult recommendation, List<String> tasteKeywords) {
        return recommendation.items().stream()
                .map(item -> new GiftProduct(
                        userId, findProduct(item), item.score(), item.reason(), tasteKeywords))
                .toList();
    }

    private Product findProduct(AiServerRecommendedItem item) {
        return productRepository.findByPlatformTypeAndExternalId(item.platform(), item.externalId())
                .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));
    }
}
