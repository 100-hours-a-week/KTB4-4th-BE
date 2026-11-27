package kr.ktb.zura.needu.product.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.function.Supplier;
import kr.ktb.zura.needu.common.redis.RedisCacheStore;
import kr.ktb.zura.needu.common.redis.RedisKeyGenerator;
import kr.ktb.zura.needu.product.dto.response.PriceRangeResponse;
import kr.ktb.zura.needu.product.repository.GiftProductRepository;
import kr.ktb.zura.needu.product.repository.PersonalProductRepository;
import kr.ktb.zura.needu.product.repository.ProductPriceRange;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ProductPriceRangeCacheServiceTest {

    private static final Long USER_ID = 1L;
    private static final String CACHE_NAME = "product-price-range";
    private static final String PERSONAL_KEY = "backend:test:product:price-range:personal:1";
    private static final String GIFT_KEY = "backend:test:product:price-range:gift:1";
    private static final Duration TTL = Duration.ofMinutes(10);

    private final PersonalProductRepository personalProductRepository = mock(PersonalProductRepository.class);
    private final GiftProductRepository giftProductRepository = mock(GiftProductRepository.class);
    private final RedisCacheStore redisCacheStore = mock(RedisCacheStore.class);
    private final ProductPriceRangeCacheService service = new ProductPriceRangeCacheService(
            personalProductRepository,
            giftProductRepository,
            redisCacheStore,
            new RedisKeyGenerator("backend:test"),
            TTL
    );

    @Test
    void cacheMiss_findPersonalPriceRange_loadsPersonalRangeWithPersonalKey() {
        givenCacheMiss(PERSONAL_KEY);
        given(personalProductRepository.findPriceRangeByUserId(USER_ID))
                .willReturn(new ProductPriceRange(BigDecimal.valueOf(1000L), BigDecimal.valueOf(5000L)));

        PriceRangeResponse response = service.findPersonalPriceRange(USER_ID);

        assertThat(response).isEqualTo(new PriceRangeResponse(1000L, 5000L));
        verify(personalProductRepository).findPriceRangeByUserId(USER_ID);
    }

    @Test
    void cacheMiss_findGiftPriceRange_loadsGiftRangeWithGiftKey() {
        givenCacheMiss(GIFT_KEY);
        given(giftProductRepository.findPriceRangeByUserId(USER_ID))
                .willReturn(new ProductPriceRange(BigDecimal.valueOf(2000L), BigDecimal.valueOf(8000L)));

        PriceRangeResponse response = service.findGiftPriceRange(USER_ID);

        assertThat(response).isEqualTo(new PriceRangeResponse(2000L, 8000L));
        verify(giftProductRepository).findPriceRangeByUserId(USER_ID);
    }

    @Test
    void noRecommendations_findPersonalPriceRange_returnsEmptyRangeToCache() {
        givenCacheMiss(PERSONAL_KEY);
        given(personalProductRepository.findPriceRangeByUserId(USER_ID))
                .willReturn(new ProductPriceRange(null, null));

        PriceRangeResponse response = service.findPersonalPriceRange(USER_ID);

        // null이 아닌 빈 범위를 반환해야 RedisCacheStore가 저장한다
        assertThat(response).isEqualTo(new PriceRangeResponse(null, null));
    }

    @Test
    void recommendationsUpdated_evictPriceRanges_deletesBothRecommendationTypes() {
        service.evictPriceRanges(new ProductRecommendationsUpdatedEvent(USER_ID));

        verify(redisCacheStore).delete(CACHE_NAME, PERSONAL_KEY, GIFT_KEY);
    }

    private void givenCacheMiss(String key) {
        given(redisCacheStore.findOrLoad(eq(CACHE_NAME), eq(key), eq(PriceRangeResponse.class), eq(TTL), any()))
                .willAnswer(invocation -> invocation.<Supplier<PriceRangeResponse>>getArgument(4).get());
    }
}
