package kr.ktb.zura.needu.product.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Duration;
import kr.ktb.zura.needu.product.dto.response.PriceRangeResponse;
import kr.ktb.zura.needu.product.repository.GiftProductRepository;
import kr.ktb.zura.needu.product.repository.PersonalProductRepository;
import kr.ktb.zura.needu.product.repository.ProductPriceRange;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ProductPriceRangeCacheServiceTest {

    private static final Long USER_ID = 1L;

    private final PersonalProductRepository personalProductRepository = mock(PersonalProductRepository.class);
    private final GiftProductRepository giftProductRepository = mock(GiftProductRepository.class);

    @Test
    void repeatedPersonalPriceRange_findPersonalPriceRange_usesCachedValue() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        ProductPriceRangeCacheService service = service(Duration.ofMinutes(10), meterRegistry);
        given(personalProductRepository.findPriceRangeByUserId(USER_ID))
                .willReturn(new ProductPriceRange(BigDecimal.valueOf(1000L), BigDecimal.valueOf(5000L)));

        PriceRangeResponse first = service.findPersonalPriceRange(USER_ID);
        PriceRangeResponse second = service.findPersonalPriceRange(USER_ID);

        assertThat(first).isEqualTo(new PriceRangeResponse(1000L, 5000L));
        assertThat(second).isEqualTo(first);
        assertThat(meterRegistry.get("cache.gets")
                .tags("cache", "product-price-range", "result", "miss")
                .functionCounter()
                .count()).isEqualTo(1.0);
        assertThat(meterRegistry.get("cache.gets")
                .tags("cache", "product-price-range", "result", "hit")
                .functionCounter()
                .count()).isEqualTo(1.0);
        verify(personalProductRepository).findPriceRangeByUserId(USER_ID);
    }

    @Test
    void sameUser_findPriceRanges_keepsPersonalAndGiftCachesSeparate() {
        ProductPriceRangeCacheService service = service(Duration.ofMinutes(10));
        given(personalProductRepository.findPriceRangeByUserId(USER_ID))
                .willReturn(new ProductPriceRange(BigDecimal.valueOf(1000L), BigDecimal.valueOf(5000L)));
        given(giftProductRepository.findPriceRangeByUserId(USER_ID))
                .willReturn(new ProductPriceRange(BigDecimal.valueOf(2000L), BigDecimal.valueOf(8000L)));

        PriceRangeResponse personal = service.findPersonalPriceRange(USER_ID);
        PriceRangeResponse gift = service.findGiftPriceRange(USER_ID);

        assertThat(personal).isEqualTo(new PriceRangeResponse(1000L, 5000L));
        assertThat(gift).isEqualTo(new PriceRangeResponse(2000L, 8000L));
        verify(personalProductRepository).findPriceRangeByUserId(USER_ID);
        verify(giftProductRepository).findPriceRangeByUserId(USER_ID);
    }

    @Test
    void noRecommendations_findPersonalPriceRange_cachesEmptyRange() {
        ProductPriceRangeCacheService service = service(Duration.ofMinutes(10));
        given(personalProductRepository.findPriceRangeByUserId(USER_ID))
                .willReturn(new ProductPriceRange(null, null));

        service.findPersonalPriceRange(USER_ID);
        PriceRangeResponse response = service.findPersonalPriceRange(USER_ID);

        assertThat(response).isEqualTo(new PriceRangeResponse(null, null));
        verify(personalProductRepository).findPriceRangeByUserId(USER_ID);
    }

    @Test
    void ttlElapsed_findPersonalPriceRange_loadsValueAgain() {
        ProductPriceRangeCacheService service = service(Duration.ofNanos(1));
        given(personalProductRepository.findPriceRangeByUserId(USER_ID))
                .willReturn(new ProductPriceRange(BigDecimal.ONE, BigDecimal.TEN));

        service.findPersonalPriceRange(USER_ID);
        service.findPersonalPriceRange(USER_ID);

        verify(personalProductRepository, times(2)).findPriceRangeByUserId(USER_ID);
    }

    @Test
    void recommendationsUpdated_evictPriceRanges_removesBothRecommendationTypes() {
        ProductPriceRangeCacheService service = service(Duration.ofMinutes(10));
        given(personalProductRepository.findPriceRangeByUserId(USER_ID))
                .willReturn(new ProductPriceRange(BigDecimal.ONE, BigDecimal.TEN));
        given(giftProductRepository.findPriceRangeByUserId(USER_ID))
                .willReturn(new ProductPriceRange(BigDecimal.TEN, BigDecimal.valueOf(100L)));
        service.findPersonalPriceRange(USER_ID);
        service.findGiftPriceRange(USER_ID);

        service.evictPriceRanges(new ProductRecommendationsUpdatedEvent(USER_ID));
        service.findPersonalPriceRange(USER_ID);
        service.findGiftPriceRange(USER_ID);

        verify(personalProductRepository, times(2)).findPriceRangeByUserId(USER_ID);
        verify(giftProductRepository, times(2)).findPriceRangeByUserId(USER_ID);
    }

    private ProductPriceRangeCacheService service(Duration ttl) {
        return service(ttl, new SimpleMeterRegistry());
    }

    private ProductPriceRangeCacheService service(Duration ttl, SimpleMeterRegistry meterRegistry) {
        return new ProductPriceRangeCacheService(
                personalProductRepository, giftProductRepository, meterRegistry, ttl, 100L);
    }
}
