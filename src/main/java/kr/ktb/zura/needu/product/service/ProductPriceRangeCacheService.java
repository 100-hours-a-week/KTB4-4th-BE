package kr.ktb.zura.needu.product.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import java.time.Duration;
import kr.ktb.zura.needu.product.dto.response.PriceRangeResponse;
import kr.ktb.zura.needu.product.repository.GiftProductRepository;
import kr.ktb.zura.needu.product.repository.PersonalProductRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
public class ProductPriceRangeCacheService {

    private static final String CACHE_NAME = "product-price-range";

    private final PersonalProductRepository personalProductRepository;
    private final GiftProductRepository giftProductRepository;
    private final Cache<CacheKey, PriceRangeResponse> cache;

    public ProductPriceRangeCacheService(
            PersonalProductRepository personalProductRepository,
            GiftProductRepository giftProductRepository,
            MeterRegistry meterRegistry,
            @Value("${needu.product.price-range-cache.ttl}") Duration ttl,
            @Value("${needu.product.price-range-cache.maximum-size}") long maximumSize
    ) {
        this.personalProductRepository = personalProductRepository;
        this.giftProductRepository = giftProductRepository;
        this.cache = CaffeineCacheMetrics.monitor(
                meterRegistry,
                Caffeine.newBuilder()
                        .recordStats()
                        .expireAfterWrite(ttl)
                        .maximumSize(maximumSize)
                        .<CacheKey, PriceRangeResponse>build(),
                CACHE_NAME
        );
    }

    public PriceRangeResponse findPersonalPriceRange(Long userId) {
        return cache.get(new CacheKey(RecommendationType.PERSONAL, userId),
                key -> PriceRangeResponse.from(personalProductRepository.findPriceRangeByUserId(key.userId())));
    }

    public PriceRangeResponse findGiftPriceRange(Long userId) {
        return cache.get(new CacheKey(RecommendationType.GIFT, userId),
                key -> PriceRangeResponse.from(giftProductRepository.findPriceRangeByUserId(key.userId())));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void evictPriceRanges(ProductRecommendationsUpdatedEvent event) {
        cache.invalidate(new CacheKey(RecommendationType.PERSONAL, event.userId()));
        cache.invalidate(new CacheKey(RecommendationType.GIFT, event.userId()));
    }

    private enum RecommendationType {
        PERSONAL,
        GIFT
    }

    private record CacheKey(RecommendationType type, Long userId) {
    }
}
