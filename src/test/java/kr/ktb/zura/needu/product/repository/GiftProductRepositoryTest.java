package kr.ktb.zura.needu.product.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import kr.ktb.zura.needu.product.entity.GiftProduct;
import kr.ktb.zura.needu.product.entity.Product;
import kr.ktb.zura.needu.product.entity.ProductFeedback;
import kr.ktb.zura.needu.product.type.PlatformType;
import kr.ktb.zura.needu.product.type.ProductCategory;
import kr.ktb.zura.needu.product.type.ProductContext;
import kr.ktb.zura.needu.product.type.ProductFeedbackType;
import kr.ktb.zura.needu.product.type.ProductStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.Limit;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@DataJpaTest
class GiftProductRepositoryTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final BigDecimal MIN_PRICE = new BigDecimal("30000");
    private static final BigDecimal MAX_PRICE = new BigDecimal("50000");

    @Autowired
    private GiftProductRepository giftProductRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void giftProductsSaved_findAllByUserIdAndPriceRange_returnsOnlyOwnItemsOrderedByScoreThenIdDesc() {
        GiftProduct low = saveGiftProduct(USER_ID, "0.100000", saveProduct("낮은 점수", "40000.00"));
        GiftProduct sameScoreFirst = saveGiftProduct(USER_ID, "0.500000", saveProduct("같은 점수 1", "40000.00"));
        GiftProduct sameScoreSecond = saveGiftProduct(USER_ID, "0.500000", saveProduct("같은 점수 2", "40000.00"));
        GiftProduct high = saveGiftProduct(USER_ID, "0.900000", saveProduct("높은 점수", "40000.00"));
        saveGiftProduct(OTHER_USER_ID, "0.990000", saveProduct("다른 사용자", "40000.00"));
        entityManager.clear();

        List<GiftProductSummary> result =
                giftProductRepository.findAllByUserIdAndPriceRange(USER_ID, MIN_PRICE, MAX_PRICE, null, Limit.of(10));

        assertThat(result).extracting(GiftProductSummary::id)
                .containsExactly(high.getId(), sameScoreSecond.getId(), sameScoreFirst.getId(), low.getId());
    }

    @Test
    void productsOutsidePriceRange_findAllByUserIdAndPriceRange_returnsOnlyItemsWithinRangeInclusive() {
        GiftProduct minBoundary = saveGiftProduct(USER_ID, "0.100000", saveProduct("최소 경계", "30000.00"));
        GiftProduct maxBoundary = saveGiftProduct(USER_ID, "0.200000", saveProduct("최대 경계", "50000.00"));
        saveGiftProduct(USER_ID, "0.900000", saveProduct("최소 미만", "29999.00"));
        saveGiftProduct(USER_ID, "0.800000", saveProduct("최대 초과", "50001.00"));
        entityManager.clear();

        List<GiftProductSummary> result =
                giftProductRepository.findAllByUserIdAndPriceRange(USER_ID, MIN_PRICE, MAX_PRICE, null, Limit.of(10));

        assertThat(result).extracting(GiftProductSummary::id).containsExactly(maxBoundary.getId(), minBoundary.getId());
    }

    @Test
    void productsFound_findPriceRangeByUserId_returnsOwnActiveProductRange() {
        saveGiftProduct(USER_ID, "0.100000", saveProduct("최저가", "10000.00"));
        saveGiftProduct(USER_ID, "0.200000", saveProduct("최고가", "90000.00"));
        saveGiftProduct(OTHER_USER_ID, "0.300000", saveProduct("다른 사용자", "1000.00"));
        Product soldOut = saveProduct("품절", "100000.00");
        ReflectionTestUtils.setField(soldOut, "status", ProductStatus.SOLD_OUT);
        saveGiftProduct(USER_ID, "0.400000", soldOut);
        entityManager.flush();
        entityManager.clear();

        ProductPriceRange result = giftProductRepository.findPriceRangeByUserId(USER_ID);

        assertThat(result.minPrice()).isEqualByComparingTo("10000.00");
        assertThat(result.maxPrice()).isEqualByComparingTo("90000.00");
    }

    @Test
    void inactiveOrDeletedProduct_findAllByUserIdAndPriceRange_excludesItem() {
        GiftProduct active = saveGiftProduct(USER_ID, "0.100000", saveProduct("판매 중", "40000.00"));
        Product soldOut = saveProduct("품절", "40000.00");
        Product deleted = saveProduct("삭제됨", "40000.00");
        // Product에는 상태 변경 도메인 메서드가 아직 없어 테스트에서만 직접 설정한다.
        ReflectionTestUtils.setField(soldOut, "status", ProductStatus.SOLD_OUT);
        ReflectionTestUtils.setField(deleted, "deletedAt", LocalDateTime.now());
        saveGiftProduct(USER_ID, "0.900000", soldOut);
        saveGiftProduct(USER_ID, "0.800000", deleted);
        entityManager.flush();
        entityManager.clear();

        List<GiftProductSummary> result =
                giftProductRepository.findAllByUserIdAndPriceRange(USER_ID, MIN_PRICE, MAX_PRICE, null, Limit.of(10));

        assertThat(result).extracting(GiftProductSummary::id).containsExactly(active.getId());
    }

    @Test
    void cursorGiven_findAllByUserIdAndPriceRangeAfterCursor_returnsItemsAfterCursorPosition() {
        GiftProduct low = saveGiftProduct(USER_ID, "0.100000", saveProduct("낮은 점수", "40000.00"));
        GiftProduct sameScoreFirst = saveGiftProduct(USER_ID, "0.500000", saveProduct("같은 점수 1", "40000.00"));
        GiftProduct sameScoreSecond = saveGiftProduct(USER_ID, "0.500000", saveProduct("같은 점수 2", "40000.00"));
        saveGiftProduct(USER_ID, "0.900000", saveProduct("높은 점수", "40000.00"));
        entityManager.clear();

        List<GiftProductSummary> result = giftProductRepository.findAllByUserIdAndPriceRangeAfterCursor(
                USER_ID, MIN_PRICE, MAX_PRICE, null, new BigDecimal("0.500000"), sameScoreSecond.getId(), Limit.of(10));

        assertThat(result).extracting(GiftProductSummary::id).containsExactly(sameScoreFirst.getId(), low.getId());
    }

    @Test
    void giftProductsFound_findAllByUserIdAndPriceRange_returnsProductFieldsAndKeywords() {
        Product lamp = saveProduct("램프", "40000.00");
        GiftProduct saved = saveGiftProduct(USER_ID, "0.100000", lamp);
        entityManager.clear();

        GiftProductSummary result = giftProductRepository
                .findAllByUserIdAndPriceRange(USER_ID, MIN_PRICE, MAX_PRICE, null, Limit.of(1)).getFirst();

        assertThat(result.id()).isEqualTo(saved.getId());
        assertThat(result.productId()).isEqualTo(lamp.getId());
        assertThat(result.productName()).isEqualTo("램프");
        assertThat(result.category()).isEqualTo(ProductCategory.LIVING);
        assertThat(result.price()).isEqualByComparingTo("40000.00");
        assertThat(result.score()).isEqualByComparingTo("0.100000");
        assertThat(result.reason()).isEqualTo("이유");
        assertThat(result.tasteKeywords()).containsExactly("인테리어", "감성");
    }

    @Test
    void deletedRecommendation_findAllByUserIdAndPriceRange_excludesItem() {
        GiftProduct kept = saveGiftProduct(USER_ID, "0.900000", saveProduct("유지", "40000.00"));
        GiftProduct deleted = saveGiftProduct(USER_ID, "0.800000", saveProduct("별로예요", "40000.00"));
        deleted.delete();
        entityManager.flush();
        entityManager.clear();

        List<GiftProductSummary> result =
                giftProductRepository.findAllByUserIdAndPriceRange(USER_ID, MIN_PRICE, MAX_PRICE, null, Limit.of(10));

        assertThat(result).extracting(GiftProductSummary::id).containsExactly(kept.getId());
    }

    @Test
    void deletedRecommendation_findAllByUserIdAndPriceRangeAfterCursor_excludesItem() {
        GiftProduct cursorItem = saveGiftProduct(USER_ID, "0.900000", saveProduct("커서", "40000.00"));
        GiftProduct deleted = saveGiftProduct(USER_ID, "0.700000", saveProduct("별로예요", "40000.00"));
        GiftProduct next = saveGiftProduct(USER_ID, "0.500000", saveProduct("다음", "40000.00"));
        deleted.delete();
        entityManager.flush();
        entityManager.clear();

        List<GiftProductSummary> result = giftProductRepository.findAllByUserIdAndPriceRangeAfterCursor(
                USER_ID, MIN_PRICE, MAX_PRICE, null, new BigDecimal("0.900000"), cursorItem.getId(), Limit.of(10));

        assertThat(result).extracting(GiftProductSummary::id).containsExactly(next.getId());
    }

    @Test
    void deletedRecommendation_findPriceRangeByUserId_excludesItemPrice() {
        saveGiftProduct(USER_ID, "0.100000", saveProduct("최저가", "10000.00"));
        saveGiftProduct(USER_ID, "0.200000", saveProduct("최고가", "90000.00"));
        GiftProduct deleted = saveGiftProduct(USER_ID, "0.300000", saveProduct("별로예요", "100000.00"));
        deleted.delete();
        entityManager.flush();
        entityManager.clear();

        ProductPriceRange result = giftProductRepository.findPriceRangeByUserId(USER_ID);

        assertThat(result.minPrice()).isEqualByComparingTo("10000.00");
        assertThat(result.maxPrice()).isEqualByComparingTo("90000.00");
    }

    @Test
    void deletedRecommendation_existsActiveByUserIdAndProductId_returnsTrue() {
        Product disliked = saveProduct("별로예요", "40000.00");
        saveGiftProduct(USER_ID, "0.100000", disliked).delete();
        entityManager.flush();
        entityManager.clear();

        assertThat(giftProductRepository.existsActiveByUserIdAndProductId(USER_ID, disliked.getId())).isTrue();
    }

    @Test
    void ownActiveRecommendation_existsActiveByUserIdAndProductId_returnsTrue() {
        Product lamp = saveProduct("램프", "40000.00");
        saveGiftProduct(USER_ID, "0.100000", lamp);
        entityManager.clear();

        assertThat(giftProductRepository.existsActiveByUserIdAndProductId(USER_ID, lamp.getId())).isTrue();
        assertThat(giftProductRepository.existsActiveByUserIdAndProductId(OTHER_USER_ID, lamp.getId())).isFalse();
    }

    @Test
    void inactiveOrDeletedProduct_existsActiveByUserIdAndProductId_returnsFalse() {
        Product soldOut = saveProduct("품절", "40000.00");
        Product deleted = saveProduct("삭제됨", "40000.00");
        ReflectionTestUtils.setField(soldOut, "status", ProductStatus.SOLD_OUT);
        ReflectionTestUtils.setField(deleted, "deletedAt", LocalDateTime.now());
        saveGiftProduct(USER_ID, "0.100000", soldOut);
        saveGiftProduct(USER_ID, "0.200000", deleted);
        entityManager.clear();

        assertThat(giftProductRepository.existsActiveByUserIdAndProductId(USER_ID, soldOut.getId())).isFalse();
        assertThat(giftProductRepository.existsActiveByUserIdAndProductId(USER_ID, deleted.getId())).isFalse();
    }

    @Test
    void categoryGiven_findAllByUserIdAndPriceRange_returnsOnlyThatCategory() {
        GiftProduct living = saveGiftProduct(USER_ID, "0.900000", saveProduct("램프", ProductCategory.LIVING));
        saveGiftProduct(USER_ID, "0.800000", saveProduct("립밤", ProductCategory.BEAUTY));
        entityManager.clear();

        List<GiftProductSummary> result = giftProductRepository.findAllByUserIdAndPriceRange(
                USER_ID, MIN_PRICE, MAX_PRICE, ProductCategory.LIVING, Limit.of(10));

        assertThat(result).extracting(GiftProductSummary::id).containsExactly(living.getId());
    }

    @Test
    void categoryAndDeletedRecommendation_findAllByUserIdAndPriceRangeAfterCursor_paginatesFilteredItems() {
        GiftProduct cursorItem = saveGiftProduct(USER_ID, "0.900000", saveProduct("커서", ProductCategory.LIVING));
        GiftProduct disliked = saveGiftProduct(USER_ID, "0.800000", saveProduct("별로예요", ProductCategory.LIVING));
        saveGiftProduct(USER_ID, "0.700000", saveProduct("다른 카테고리", ProductCategory.BEAUTY));
        GiftProduct next = saveGiftProduct(USER_ID, "0.600000", saveProduct("다음", ProductCategory.LIVING));
        disliked.delete();
        entityManager.flush();
        entityManager.clear();

        List<GiftProductSummary> result = giftProductRepository.findAllByUserIdAndPriceRangeAfterCursor(
                USER_ID, MIN_PRICE, MAX_PRICE, ProductCategory.LIVING,
                new BigDecimal("0.900000"), cursorItem.getId(), Limit.of(10));

        assertThat(result).extracting(GiftProductSummary::id).containsExactly(next.getId());
    }

    @Test
    void feedbackSaved_findAllByUserIdAndPriceRange_returnsOnlyOwnersMyGiftFeedback() {
        Product liked = saveProduct("마음에 들어요", "40000.00");
        Product likedInPersonal = saveProduct("개인 추천에서만 마음에 들어요", "40000.00");
        Product likedByOther = saveProduct("다른 사용자만 마음에 들어요", "40000.00");
        GiftProduct likedItem = saveGiftProduct(USER_ID, "0.900000", liked);
        GiftProduct likedInPersonalItem = saveGiftProduct(USER_ID, "0.800000", likedInPersonal);
        GiftProduct likedByOtherItem = saveGiftProduct(USER_ID, "0.700000", likedByOther);
        saveFeedback(USER_ID, liked, ProductContext.MY_GIFT);
        saveFeedback(USER_ID, likedInPersonal, ProductContext.PERSONAL);
        saveFeedback(OTHER_USER_ID, likedByOther, ProductContext.MY_GIFT);
        entityManager.clear();

        List<GiftProductSummary> result = giftProductRepository.findAllByUserIdAndPriceRange(
                USER_ID, MIN_PRICE, MAX_PRICE, null, Limit.of(10));

        assertThat(result).extracting(GiftProductSummary::id, GiftProductSummary::feedback)
                .containsExactly(
                        tuple(likedItem.getId(), ProductFeedbackType.LIKE),
                        tuple(likedInPersonalItem.getId(), null),
                        tuple(likedByOtherItem.getId(), null));
    }

    private Product saveProduct(String name, String price) {
        return entityManager.persist(new Product(
                PlatformType.COUPANG, name, name, ProductCategory.LIVING, null,
                new BigDecimal(price), null, null, null));
    }

    private Product saveProduct(String name, ProductCategory category) {
        return entityManager.persist(new Product(
                PlatformType.COUPANG, name, name, category, null,
                new BigDecimal("40000.00"), null, null, null));
    }

    private void saveFeedback(Long userId, Product product, ProductContext context) {
        entityManager.persistAndFlush(new ProductFeedback(userId, product, context, ProductFeedbackType.LIKE));
    }

    private GiftProduct saveGiftProduct(Long userId, String score, Product product) {
        return entityManager.persistAndFlush(new GiftProduct(
                userId, product, new BigDecimal(score), "이유", List.of("인테리어", "감성")));
    }
}
