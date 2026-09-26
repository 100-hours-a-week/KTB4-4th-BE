package kr.ktb.zura.needu.product.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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

// 재분석으로 이미 추천된 상품이 다시 오면 행을 추가하지 않고 적합도(score)만 갱신한다
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
        // 없는 상품이 하나라도 있으면 어느 쪽도 저장하지 않도록 상품 조회를 먼저 끝낸다
        List<RecommendedProduct> personalProducts = toRecommendedProducts(self);
        List<RecommendedProduct> giftProducts = toRecommendedProducts(gift);
        savePersonalProducts(userId, personalProducts);
        saveGiftProducts(userId, giftProducts, tasteKeywords);
    }

    private void savePersonalProducts(Long userId, List<RecommendedProduct> recommendedProducts) {
        Map<Long, List<PersonalProduct>> existingProducts = personalProductRepository
                .findAllByUserIdAndProductIdIn(userId, toProductIds(recommendedProducts)).stream()
                .collect(Collectors.groupingBy(personalProduct -> personalProduct.getProduct().getId()));
        List<PersonalProduct> newProducts = new ArrayList<>();
        for (RecommendedProduct recommended : recommendedProducts) {
            List<PersonalProduct> sameProducts = existingProducts.get(recommended.product().getId());
            if (sameProducts != null) {
                sameProducts.forEach(personalProduct -> personalProduct.updateScore(recommended.score()));
                continue;
            }
            PersonalProduct personalProduct = new PersonalProduct(
                    userId, recommended.product(), recommended.score(), recommended.reason());
            newProducts.add(personalProduct);
            // 같은 응답 안에 같은 상품이 두 번 오면 새로 만든 행의 점수를 갱신한다
            existingProducts.put(recommended.product().getId(), List.of(personalProduct));
        }
        personalProductRepository.saveAll(newProducts);
    }

    private void saveGiftProducts(Long userId, List<RecommendedProduct> recommendedProducts, List<String> tasteKeywords) {
        Map<Long, List<GiftProduct>> existingProducts = giftProductRepository
                .findAllByUserIdAndProductIdIn(userId, toProductIds(recommendedProducts)).stream()
                .collect(Collectors.groupingBy(giftProduct -> giftProduct.getProduct().getId()));
        List<GiftProduct> newProducts = new ArrayList<>();
        for (RecommendedProduct recommended : recommendedProducts) {
            List<GiftProduct> sameProducts = existingProducts.get(recommended.product().getId());
            if (sameProducts != null) {
                sameProducts.forEach(giftProduct -> giftProduct.updateScore(recommended.score()));
                continue;
            }
            GiftProduct giftProduct = new GiftProduct(
                    userId, recommended.product(), recommended.score(), recommended.reason(), tasteKeywords);
            newProducts.add(giftProduct);
            existingProducts.put(recommended.product().getId(), List.of(giftProduct));
        }
        giftProductRepository.saveAll(newProducts);
    }

    private List<RecommendedProduct> toRecommendedProducts(AiServerRecommendationResult recommendation) {
        return recommendation.items().stream()
                .map(item -> new RecommendedProduct(findProduct(item), item.score(), item.reason()))
                .toList();
    }

    private List<Long> toProductIds(List<RecommendedProduct> recommendedProducts) {
        return recommendedProducts.stream()
                .map(recommended -> recommended.product().getId())
                .distinct()
                .toList();
    }

    private Product findProduct(AiServerRecommendedItem item) {
        return productRepository.findByPlatformTypeAndExternalId(item.platform(), item.externalId())
                .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));
    }

    private record RecommendedProduct(Product product, BigDecimal score, String reason) {
    }
}
