package kr.ktb.zura.needu.product.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import kr.ktb.zura.needu.product.dto.response.ProductActivityResponse;
import kr.ktb.zura.needu.product.repository.ProductFeedbackRepository;
import kr.ktb.zura.needu.product.repository.PurchaseCheckRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProductActivityService {

    private final ProductFeedbackRepository productFeedbackRepository;
    private final PurchaseCheckRepository purchaseCheckRepository;
    private final Clock clock;
    private final Duration lookback;
    private final int maxItems;

    public ProductActivityService(
            ProductFeedbackRepository productFeedbackRepository,
            PurchaseCheckRepository purchaseCheckRepository,
            Clock clock,
            @Value("${needu.product.activity.lookback}") Duration lookback,
            @Value("${needu.product.activity.max-items}") int maxItems
    ) {
        this.productFeedbackRepository = productFeedbackRepository;
        this.purchaseCheckRepository = purchaseCheckRepository;
        this.clock = clock;
        this.lookback = lookback;
        this.maxItems = maxItems;
    }

    public List<ProductActivityResponse> findAllRecentActivities(Long userId) {
        LocalDateTime since = LocalDateTime.now(clock).minus(lookback);
        Limit limit = Limit.of(maxItems);
        Stream<ProductActivityResponse> feedbacks = productFeedbackRepository
                .findAllActivitiesByUserIdSince(userId, since, limit).stream()
                .map(ProductActivityResponse::from);
        Stream<ProductActivityResponse> purchases = purchaseCheckRepository
                .findAllPurchasedActivitiesRelatedToUserIdSince(userId, since, limit).stream()
                .map(ProductActivityResponse::from);
        return Stream.concat(feedbacks, purchases)
                .sorted(Comparator.comparing(ProductActivityResponse::occurredAt).reversed())
                .limit(maxItems)
                .toList();
    }
}
