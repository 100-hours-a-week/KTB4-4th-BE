package kr.ktb.zura.needu.product.service;

import java.time.Duration;
import kr.ktb.zura.needu.common.redis.RedisCacheStore;
import kr.ktb.zura.needu.common.redis.RedisKeyGenerator;
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
    private static final String KEY_DOMAIN = "product";
    private static final String KEY_PURPOSE = "price-range";
    private static final String PERSONAL = "personal";
    private static final String GIFT = "gift";

    private final PersonalProductRepository personalProductRepository;
    private final GiftProductRepository giftProductRepository;
    private final RedisCacheStore redisCacheStore;
    private final RedisKeyGenerator keyGenerator;
    private final Duration ttl;

    public ProductPriceRangeCacheService(
            PersonalProductRepository personalProductRepository,
            GiftProductRepository giftProductRepository,
            RedisCacheStore redisCacheStore,
            RedisKeyGenerator keyGenerator,
            @Value("${needu.product.price-range-cache.ttl}") Duration ttl
    ) {
        this.personalProductRepository = personalProductRepository;
        this.giftProductRepository = giftProductRepository;
        this.redisCacheStore = redisCacheStore;
        this.keyGenerator = keyGenerator;
        this.ttl = ttl;
    }

    public PriceRangeResponse findPersonalPriceRange(Long userId) {
        return redisCacheStore.findOrLoad(CACHE_NAME, generateKey(PERSONAL, userId), PriceRangeResponse.class, ttl,
                () -> PriceRangeResponse.from(personalProductRepository.findPriceRangeByUserId(userId)));
    }

    public PriceRangeResponse findGiftPriceRange(Long userId) {
        return redisCacheStore.findOrLoad(CACHE_NAME, generateKey(GIFT, userId), PriceRangeResponse.class, ttl,
                () -> PriceRangeResponse.from(giftProductRepository.findPriceRangeByUserId(userId)));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void evictPriceRanges(ProductRecommendationsUpdatedEvent event) {
        redisCacheStore.delete(CACHE_NAME,
                generateKey(PERSONAL, event.userId()),
                generateKey(GIFT, event.userId()));
    }

    private String generateKey(String recommendationType, Long userId) {
        return keyGenerator.generate(KEY_DOMAIN, KEY_PURPOSE, recommendationType, userId);
    }
}
