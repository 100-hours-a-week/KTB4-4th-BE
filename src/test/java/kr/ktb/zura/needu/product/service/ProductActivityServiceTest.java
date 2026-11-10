package kr.ktb.zura.needu.product.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import kr.ktb.zura.needu.product.dto.response.ProductActivityResponse;
import kr.ktb.zura.needu.product.repository.ProductFeedbackActivity;
import kr.ktb.zura.needu.product.repository.ProductFeedbackRepository;
import kr.ktb.zura.needu.product.repository.PurchaseActivity;
import kr.ktb.zura.needu.product.repository.PurchaseCheckRepository;
import kr.ktb.zura.needu.product.type.PlatformType;
import kr.ktb.zura.needu.product.type.ProductActivityType;
import kr.ktb.zura.needu.product.type.ProductContext;
import kr.ktb.zura.needu.product.type.ProductFeedbackType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Limit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ProductActivityServiceTest {

    private static final Long USER_ID = 1L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 2, 7, 0);
    private static final Duration LOOKBACK = Duration.ofDays(90);
    private static final int MAX_ITEMS = 3;

    private ProductFeedbackRepository productFeedbackRepository;
    private PurchaseCheckRepository purchaseCheckRepository;
    private ProductActivityService productActivityService;

    @BeforeEach
    void setUp() {
        productFeedbackRepository = mock(ProductFeedbackRepository.class);
        purchaseCheckRepository = mock(PurchaseCheckRepository.class);
        Clock clock = Clock.fixed(Instant.from(NOW.atOffset(ZoneOffset.UTC)), ZoneOffset.UTC);
        productActivityService = new ProductActivityService(
                productFeedbackRepository, purchaseCheckRepository, clock, LOOKBACK, MAX_ITEMS);
    }

    @Test
    void feedbacksAndPurchases_findAllRecentActivities_mergesNewestFirstWithActions() {
        given(productFeedbackRepository.findAllActivitiesByUserIdSince(eq(USER_ID), any(), any()))
                .willReturn(List.of(
                        feedback("dislike", ProductFeedbackType.DISLIKE, NOW.minusDays(1)),
                        feedback("like", ProductFeedbackType.LIKE, NOW.minusDays(5))));
        given(purchaseCheckRepository.findAllPurchasedActivitiesRelatedToUserIdSince(eq(USER_ID), any(), any()))
                .willReturn(List.of(purchase("received", ProductContext.FRIEND_GIFT, NOW.minusDays(3))));

        List<ProductActivityResponse> result = productActivityService.findAllRecentActivities(USER_ID);

        assertThat(result)
                .extracting(ProductActivityResponse::externalId, ProductActivityResponse::activityType)
                .containsExactly(
                        tuple("dislike", ProductActivityType.DISLIKE),
                        tuple("received", ProductActivityType.RECEIVED),
                        tuple("like", ProductActivityType.LIKE));
    }

    @Test
    void ownPurchase_findAllRecentActivities_returnsPurchased() {
        given(purchaseCheckRepository.findAllPurchasedActivitiesRelatedToUserIdSince(eq(USER_ID), any(), any()))
                .willReturn(List.of(
                        purchase("personal", ProductContext.PERSONAL, NOW.minusDays(1)),
                        purchase("my-gift", ProductContext.MY_GIFT, NOW.minusDays(2))));

        List<ProductActivityResponse> result = productActivityService.findAllRecentActivities(USER_ID);

        assertThat(result).extracting(ProductActivityResponse::activityType)
                .containsExactly(ProductActivityType.PURCHASED, ProductActivityType.PURCHASED);
    }

    @Test
    void moreThanMaxItems_findAllRecentActivities_keepsNewestOnly() {
        given(productFeedbackRepository.findAllActivitiesByUserIdSince(eq(USER_ID), any(), any()))
                .willReturn(List.of(
                        feedback("feedback-1", ProductFeedbackType.LIKE, NOW.minusDays(1)),
                        feedback("feedback-4", ProductFeedbackType.LIKE, NOW.minusDays(4))));
        given(purchaseCheckRepository.findAllPurchasedActivitiesRelatedToUserIdSince(eq(USER_ID), any(), any()))
                .willReturn(List.of(
                        purchase("purchase-2", ProductContext.PERSONAL, NOW.minusDays(2)),
                        purchase("purchase-3", ProductContext.PERSONAL, NOW.minusDays(3))));

        List<ProductActivityResponse> result = productActivityService.findAllRecentActivities(USER_ID);

        assertThat(result).extracting(ProductActivityResponse::externalId)
                .containsExactly("feedback-1", "purchase-2", "purchase-3");
    }

    @Test
    void lookbackAndMaxItems_findAllRecentActivities_queriesOnlyRecentRange() {
        productActivityService.findAllRecentActivities(USER_ID);

        verify(productFeedbackRepository)
                .findAllActivitiesByUserIdSince(USER_ID, NOW.minus(LOOKBACK), Limit.of(MAX_ITEMS));
        verify(purchaseCheckRepository)
                .findAllPurchasedActivitiesRelatedToUserIdSince(USER_ID, NOW.minus(LOOKBACK), Limit.of(MAX_ITEMS));
    }

    @Test
    void noActivity_findAllRecentActivities_returnsEmpty() {
        assertThat(productActivityService.findAllRecentActivities(USER_ID)).isEmpty();
    }

    private static ProductFeedbackActivity feedback(String externalId, ProductFeedbackType type, LocalDateTime at) {
        return new ProductFeedbackActivity(PlatformType.COUPANG, externalId, type, ProductContext.PERSONAL, at);
    }

    private static PurchaseActivity purchase(String externalId, ProductContext context, LocalDateTime at) {
        return new PurchaseActivity(PlatformType.COUPANG, externalId, context, at);
    }
}
