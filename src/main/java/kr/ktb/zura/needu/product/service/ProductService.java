package kr.ktb.zura.needu.product.service;

import java.util.Collection;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.CommonErrorCode;
import kr.ktb.zura.needu.product.dto.response.ProductCategoryResponse;
import kr.ktb.zura.needu.product.dto.response.ProductDetailResponse;
import kr.ktb.zura.needu.product.dto.response.ProductRecommendationResponse;
import kr.ktb.zura.needu.product.dto.response.ProductSummaryResponse;
import kr.ktb.zura.needu.product.entity.GiftProduct;
import kr.ktb.zura.needu.product.entity.PersonalProduct;
import kr.ktb.zura.needu.product.entity.ProductFeedback;
import kr.ktb.zura.needu.product.exception.ProductErrorCode;
import kr.ktb.zura.needu.product.repository.GiftProductRepository;
import kr.ktb.zura.needu.product.repository.PersonalProductRepository;
import kr.ktb.zura.needu.product.repository.ProductFeedbackRepository;
import kr.ktb.zura.needu.product.repository.ProductRepository;
import kr.ktb.zura.needu.product.type.ProductCategory;
import kr.ktb.zura.needu.product.type.ProductContext;
import kr.ktb.zura.needu.product.type.ProductFeedbackType;
import kr.ktb.zura.needu.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final UserService userService;
    private final GiftProductService giftProductService;
    private final PersonalProductRepository personalProductRepository;
    private final GiftProductRepository giftProductRepository;
    private final ProductFeedbackRepository productFeedbackRepository;
    private final ProductRepository productRepository;

    public ProductSummaryResponse findProductSummary(Long productId) {
        return productRepository.findById(productId)
                .map(ProductSummaryResponse::from)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));
    }

    public Map<Long, ProductSummaryResponse> findAllProductSummaries(Collection<Long> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, ProductSummaryResponse> products = productRepository.findAllById(productIds).stream()
                .map(ProductSummaryResponse::from)
                .collect(Collectors.toMap(ProductSummaryResponse::productId, product -> product));
        if (products.size() != productIds.stream().distinct().count()) {
            throw new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND);
        }
        return products;
    }

    public List<ProductCategoryResponse> findAllCategories() {
        return Arrays.stream(ProductCategory.values())
                .map(ProductCategoryResponse::from)
                .toList();
    }

    public ProductDetailResponse findProduct(Long userId, Long productId, ProductContext context, Long friendUserId) {
        validateFriendUserId(context, friendUserId);
        userService.validateActiveUser(userId);
        return switch (context) {
            case PERSONAL -> findPersonalProduct(userId, productId);
            case MY_GIFT -> findMyGiftProduct(userId, productId);
            case FRIEND_GIFT -> findFriendGiftProduct(userId, friendUserId, productId);
        };
    }

    public void validateGiftProduct(Long recipientUserId, Long productId) {
        findGiftProduct(recipientUserId, productId);
    }

    private void validateFriendUserId(ProductContext context, Long friendUserId) {
        if (context == ProductContext.FRIEND_GIFT && friendUserId == null) {
            throw new BusinessException(CommonErrorCode.COMMON_INVALID_INPUT);
        }
    }

    private ProductDetailResponse findPersonalProduct(Long userId, Long productId) {
        PersonalProduct personalProduct = personalProductRepository.findAllActiveByUserIdAndProductId(userId, productId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_RECOMMENDATION_NOT_FOUND));
        return ProductDetailResponse.from(
                personalProduct.getProduct(),
                ProductRecommendationResponse.from(personalProduct),
                findMyFeedback(userId, productId, ProductContext.PERSONAL));
    }

    private ProductDetailResponse findMyGiftProduct(Long userId, Long productId) {
        GiftProduct giftProduct = findGiftProduct(userId, productId);
        return ProductDetailResponse.from(
                giftProduct.getProduct(),
                ProductRecommendationResponse.from(giftProduct),
                findMyFeedback(userId, productId, ProductContext.MY_GIFT));
    }

    private ProductDetailResponse findFriendGiftProduct(Long userId, Long friendUserId, Long productId) {
        giftProductService.validateFriendGiftViewable(userId, friendUserId);
        GiftProduct giftProduct = findGiftProduct(friendUserId, productId);
        return ProductDetailResponse.from(
                giftProduct.getProduct(), ProductRecommendationResponse.from(giftProduct), null);
    }

    private GiftProduct findGiftProduct(Long userId, Long productId) {
        return giftProductRepository.findAllActiveByUserIdAndProductId(userId, productId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_RECOMMENDATION_NOT_FOUND));
    }

    private ProductFeedbackType findMyFeedback(Long userId, Long productId, ProductContext context) {
        return productFeedbackRepository.findByUserIdAndProductIdAndContext(userId, productId, context)
                .map(ProductFeedback::getFeedbackType)
                .orElse(null);
    }
}
