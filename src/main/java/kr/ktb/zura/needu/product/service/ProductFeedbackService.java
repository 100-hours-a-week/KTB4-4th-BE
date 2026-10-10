package kr.ktb.zura.needu.product.service;

import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.CommonErrorCode;
import kr.ktb.zura.needu.product.dto.response.ProductFeedbackResponse;
import kr.ktb.zura.needu.product.entity.GiftProduct;
import kr.ktb.zura.needu.product.entity.PersonalProduct;
import kr.ktb.zura.needu.product.entity.ProductFeedback;
import kr.ktb.zura.needu.product.exception.ProductErrorCode;
import kr.ktb.zura.needu.product.repository.GiftProductRepository;
import kr.ktb.zura.needu.product.repository.PersonalProductRepository;
import kr.ktb.zura.needu.product.repository.ProductFeedbackRepository;
import kr.ktb.zura.needu.product.repository.ProductRepository;
import kr.ktb.zura.needu.product.type.ProductContext;
import kr.ktb.zura.needu.product.type.ProductFeedbackType;
import kr.ktb.zura.needu.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductFeedbackService {

    private final UserService userService;
    private final ProductFeedbackRepository productFeedbackRepository;
    private final PersonalProductRepository personalProductRepository;
    private final GiftProductRepository giftProductRepository;
    private final ProductRepository productRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public ProductFeedbackResponse updateFeedback(
            Long userId, Long productId, ProductContext context, ProductFeedbackType feedback) {
        userService.validateActiveUser(userId);
        validateRecommended(userId, productId, context);
        validateNotDisliked(userId, productId, feedback);

        productFeedbackRepository.findByUserIdAndProductIdAndContext(userId, productId, context)
                .ifPresentOrElse(
                        productFeedback -> productFeedback.updateFeedback(feedback),
                        () -> createFeedback(userId, productId, context, feedback));
        if (feedback == ProductFeedbackType.DISLIKE) {
            deleteRecommendations(userId, productId);
        }
        return new ProductFeedbackResponse(productId, context, feedback);
    }

    private void validateRecommended(Long userId, Long productId, ProductContext context) {
        boolean isRecommended = switch (context) {
            case PERSONAL -> personalProductRepository.existsActiveByUserIdAndProductId(userId, productId);
            case MY_GIFT -> giftProductRepository.existsActiveByUserIdAndProductId(userId, productId);
            case FRIEND_GIFT -> throw new BusinessException(CommonErrorCode.COMMON_INVALID_INPUT);
        };
        if (!isRecommended) {
            throw new BusinessException(ProductErrorCode.PRODUCT_RECOMMENDATION_NOT_FOUND);
        }
    }

    private void validateNotDisliked(Long userId, Long productId, ProductFeedbackType feedback) {
        if (feedback != ProductFeedbackType.DISLIKE && productFeedbackRepository
                .existsByUserIdAndProductIdAndFeedbackType(userId, productId, ProductFeedbackType.DISLIKE)) {
            throw new BusinessException(ProductErrorCode.PRODUCT_FEEDBACK_ALREADY_DISLIKED);
        }
    }

    private void deleteRecommendations(Long userId, Long productId) {
        personalProductRepository.findAllByUserIdAndProductId(userId, productId).forEach(PersonalProduct::delete);
        giftProductRepository.findAllByUserIdAndProductId(userId, productId).forEach(GiftProduct::delete);

        eventPublisher.publishEvent(new ProductRecommendationsUpdatedEvent(userId));
    }

    private void createFeedback(Long userId, Long productId, ProductContext context, ProductFeedbackType feedback) {
        productFeedbackRepository.save(
                new ProductFeedback(userId, productRepository.getReferenceById(productId), context, feedback));
    }
}
