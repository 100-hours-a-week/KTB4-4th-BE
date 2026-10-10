package kr.ktb.zura.needu.product.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import kr.ktb.zura.needu.product.entity.PersonalProduct;
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
class PersonalProductRepositoryTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final BigDecimal MIN_PRICE = new BigDecimal("30000");
    private static final BigDecimal MAX_PRICE = new BigDecimal("50000");

    @Autowired
    private PersonalProductRepository personalProductRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void personalProductsSaved_findAllByUserIdAndPriceRange_returnsOnlyOwnItemsOrderedByScoreThenIdDesc() {
        PersonalProduct low = savePersonalProduct(USER_ID, "0.100000", saveProduct("낮은 점수"));
        PersonalProduct sameScoreFirst = savePersonalProduct(USER_ID, "0.500000", saveProduct("같은 점수 1"));
        PersonalProduct sameScoreSecond = savePersonalProduct(USER_ID, "0.500000", saveProduct("같은 점수 2"));
        PersonalProduct high = savePersonalProduct(USER_ID, "0.900000", saveProduct("높은 점수"));
        savePersonalProduct(OTHER_USER_ID, "0.990000", saveProduct("다른 사용자"));
        entityManager.clear();

        List<PersonalProductSummary> result =
                personalProductRepository.findAllByUserIdAndPriceRange(USER_ID, MIN_PRICE, MAX_PRICE, null, Limit.of(10));

        assertThat(result).extracting(PersonalProductSummary::id)
                .containsExactly(high.getId(), sameScoreSecond.getId(), sameScoreFirst.getId(), low.getId());
    }

    @Test
    void limitGiven_findAllByUserIdAndPriceRange_returnsAtMostLimitItems() {
        savePersonalProduct(USER_ID, "0.100000", saveProduct("상품 1"));
        savePersonalProduct(USER_ID, "0.200000", saveProduct("상품 2"));
        savePersonalProduct(USER_ID, "0.300000", saveProduct("상품 3"));

        assertThat(personalProductRepository
                .findAllByUserIdAndPriceRange(USER_ID, MIN_PRICE, MAX_PRICE, null, Limit.of(2))).hasSize(2);
    }

    @Test
    void productsOutsidePriceRange_findAllByUserIdAndPriceRange_returnsOnlyItemsWithinRangeInclusive() {
        PersonalProduct minBoundary =
                savePersonalProduct(USER_ID, "0.100000", saveProduct("최소 경계", "30000.00"));
        PersonalProduct maxBoundary =
                savePersonalProduct(USER_ID, "0.200000", saveProduct("최대 경계", "50000.00"));
        savePersonalProduct(USER_ID, "0.900000", saveProduct("최소 미만", "29999.00"));
        savePersonalProduct(USER_ID, "0.800000", saveProduct("최대 초과", "50001.00"));
        entityManager.clear();

        List<PersonalProductSummary> result =
                personalProductRepository.findAllByUserIdAndPriceRange(USER_ID, MIN_PRICE, MAX_PRICE, null, Limit.of(10));

        assertThat(result).extracting(PersonalProductSummary::id)
                .containsExactly(maxBoundary.getId(), minBoundary.getId());
    }

    @Test
    void productsFound_findPriceRangeByUserId_returnsOwnActiveProductRange() {
        savePersonalProduct(USER_ID, "0.100000", saveProduct("최저가", "10000.00"));
        savePersonalProduct(USER_ID, "0.200000", saveProduct("최고가", "90000.00"));
        savePersonalProduct(OTHER_USER_ID, "0.300000", saveProduct("다른 사용자", "1000.00"));
        Product soldOut = saveProduct("품절", "100000.00");
        ReflectionTestUtils.setField(soldOut, "status", ProductStatus.SOLD_OUT);
        savePersonalProduct(USER_ID, "0.400000", soldOut);
        entityManager.flush();
        entityManager.clear();

        ProductPriceRange result = personalProductRepository.findPriceRangeByUserId(USER_ID);

        assertThat(result.minPrice()).isEqualByComparingTo("10000.00");
        assertThat(result.maxPrice()).isEqualByComparingTo("90000.00");
    }

    @Test
    void inactiveOrDeletedProduct_findAllByUserIdAndPriceRange_excludesItem() {
        PersonalProduct active = savePersonalProduct(USER_ID, "0.100000", saveProduct("판매 중"));
        Product soldOut = saveProduct("품절");
        Product deleted = saveProduct("삭제됨");
        // Product에는 상태 변경 도메인 메서드가 아직 없어 테스트에서만 직접 설정한다.
        ReflectionTestUtils.setField(soldOut, "status", ProductStatus.SOLD_OUT);
        ReflectionTestUtils.setField(deleted, "deletedAt", LocalDateTime.now());
        savePersonalProduct(USER_ID, "0.900000", soldOut);
        savePersonalProduct(USER_ID, "0.800000", deleted);
        entityManager.flush();
        entityManager.clear();

        List<PersonalProductSummary> result =
                personalProductRepository.findAllByUserIdAndPriceRange(USER_ID, MIN_PRICE, MAX_PRICE, null, Limit.of(10));

        assertThat(result).extracting(PersonalProductSummary::id).containsExactly(active.getId());
    }

    @Test
    void cursorGiven_findAllByUserIdAndPriceRangeAfterCursor_returnsItemsAfterCursorPosition() {
        PersonalProduct low = savePersonalProduct(USER_ID, "0.100000", saveProduct("낮은 점수"));
        PersonalProduct sameScoreFirst = savePersonalProduct(USER_ID, "0.500000", saveProduct("같은 점수 1"));
        PersonalProduct sameScoreSecond = savePersonalProduct(USER_ID, "0.500000", saveProduct("같은 점수 2"));
        savePersonalProduct(USER_ID, "0.900000", saveProduct("높은 점수"));
        entityManager.clear();

        List<PersonalProductSummary> result = personalProductRepository.findAllByUserIdAndPriceRangeAfterCursor(
                USER_ID, MIN_PRICE, MAX_PRICE, null,
                new BigDecimal("0.500000"), sameScoreSecond.getId(), Limit.of(10));

        assertThat(result).extracting(PersonalProductSummary::id).containsExactly(sameScoreFirst.getId(), low.getId());
    }

    @Test
    void personalProductsFound_findAllByUserIdAndPriceRange_returnsProductFields() {
        Product lamp = saveProduct("램프");
        PersonalProduct saved = savePersonalProduct(USER_ID, "0.100000", lamp);
        entityManager.clear();

        PersonalProductSummary result = personalProductRepository
                .findAllByUserIdAndPriceRange(USER_ID, MIN_PRICE, MAX_PRICE, null, Limit.of(1)).getFirst();

        assertThat(result.id()).isEqualTo(saved.getId());
        assertThat(result.productId()).isEqualTo(lamp.getId());
        assertThat(result.productName()).isEqualTo("램프");
        assertThat(result.score()).isEqualByComparingTo("0.100000");
    }

    @Test
    void productIdsGiven_findAllByUserIdAndProductIdIn_returnsOnlyOwnItemsForThoseProducts() {
        Product lamp = saveProduct("램프");
        Product mug = saveProduct("머그컵");
        PersonalProduct ownLamp = savePersonalProduct(USER_ID, "0.100000", lamp);
        savePersonalProduct(USER_ID, "0.200000", mug);
        savePersonalProduct(OTHER_USER_ID, "0.300000", lamp);
        entityManager.clear();

        List<PersonalProduct> result =
                personalProductRepository.findAllByUserIdAndProductIdIn(USER_ID, List.of(lamp.getId()));

        assertThat(result).extracting(PersonalProduct::getId).containsExactly(ownLamp.getId());
    }

    @Test
    void deletedRecommendation_findAllByUserIdAndPriceRange_excludesItem() {
        PersonalProduct kept = savePersonalProduct(USER_ID, "0.900000", saveProduct("유지"));
        PersonalProduct deleted = savePersonalProduct(USER_ID, "0.800000", saveProduct("별로예요"));
        deleted.delete();
        entityManager.flush();
        entityManager.clear();

        List<PersonalProductSummary> result =
                personalProductRepository.findAllByUserIdAndPriceRange(USER_ID, MIN_PRICE, MAX_PRICE, null, Limit.of(10));

        assertThat(result).extracting(PersonalProductSummary::id).containsExactly(kept.getId());
    }

    @Test
    void deletedRecommendation_findAllByUserIdAndPriceRangeAfterCursor_excludesItem() {
        PersonalProduct cursorItem = savePersonalProduct(USER_ID, "0.900000", saveProduct("커서"));
        PersonalProduct deleted = savePersonalProduct(USER_ID, "0.700000", saveProduct("별로예요"));
        PersonalProduct next = savePersonalProduct(USER_ID, "0.500000", saveProduct("다음"));
        deleted.delete();
        entityManager.flush();
        entityManager.clear();

        List<PersonalProductSummary> result = personalProductRepository.findAllByUserIdAndPriceRangeAfterCursor(
                USER_ID, MIN_PRICE, MAX_PRICE, null, new BigDecimal("0.900000"), cursorItem.getId(), Limit.of(10));

        assertThat(result).extracting(PersonalProductSummary::id).containsExactly(next.getId());
    }

    @Test
    void deletedRecommendation_findPriceRangeByUserId_excludesItemPrice() {
        savePersonalProduct(USER_ID, "0.100000", saveProduct("최저가", "10000.00"));
        savePersonalProduct(USER_ID, "0.200000", saveProduct("최고가", "90000.00"));
        PersonalProduct deleted = savePersonalProduct(USER_ID, "0.300000", saveProduct("별로예요", "100000.00"));
        deleted.delete();
        entityManager.flush();
        entityManager.clear();

        ProductPriceRange result = personalProductRepository.findPriceRangeByUserId(USER_ID);

        assertThat(result.minPrice()).isEqualByComparingTo("10000.00");
        assertThat(result.maxPrice()).isEqualByComparingTo("90000.00");
    }

    @Test
    void deletedRecommendation_existsActiveByUserIdAndProductId_returnsTrue() {
        Product disliked = saveProduct("별로예요");
        savePersonalProduct(USER_ID, "0.100000", disliked).delete();
        entityManager.flush();
        entityManager.clear();

        assertThat(personalProductRepository.existsActiveByUserIdAndProductId(USER_ID, disliked.getId())).isTrue();
    }

    @Test
    void ownActiveRecommendation_existsActiveByUserIdAndProductId_returnsTrue() {
        Product lamp = saveProduct("램프");
        savePersonalProduct(USER_ID, "0.100000", lamp);
        entityManager.clear();

        assertThat(personalProductRepository.existsActiveByUserIdAndProductId(USER_ID, lamp.getId())).isTrue();
        assertThat(personalProductRepository.existsActiveByUserIdAndProductId(OTHER_USER_ID, lamp.getId())).isFalse();
    }

    @Test
    void inactiveOrDeletedProduct_existsActiveByUserIdAndProductId_returnsFalse() {
        Product soldOut = saveProduct("품절");
        Product deleted = saveProduct("삭제됨");
        ReflectionTestUtils.setField(soldOut, "status", ProductStatus.SOLD_OUT);
        ReflectionTestUtils.setField(deleted, "deletedAt", LocalDateTime.now());
        savePersonalProduct(USER_ID, "0.100000", soldOut);
        savePersonalProduct(USER_ID, "0.200000", deleted);
        entityManager.clear();

        assertThat(personalProductRepository.existsActiveByUserIdAndProductId(USER_ID, soldOut.getId())).isFalse();
        assertThat(personalProductRepository.existsActiveByUserIdAndProductId(USER_ID, deleted.getId())).isFalse();
    }

    @Test
    void categoryGiven_findAllByUserIdAndPriceRange_returnsOnlyThatCategory() {
        PersonalProduct living = savePersonalProduct(USER_ID, "0.900000", saveProduct("램프", ProductCategory.LIVING));
        savePersonalProduct(USER_ID, "0.800000", saveProduct("립밤", ProductCategory.BEAUTY));
        entityManager.clear();

        List<PersonalProductSummary> result = personalProductRepository.findAllByUserIdAndPriceRange(
                USER_ID, MIN_PRICE, MAX_PRICE, ProductCategory.LIVING, Limit.of(10));

        assertThat(result).extracting(PersonalProductSummary::id).containsExactly(living.getId());
    }

    @Test
    void categoryAndDeletedRecommendation_findAllByUserIdAndPriceRangeAfterCursor_paginatesFilteredItems() {
        PersonalProduct cursorItem =
                savePersonalProduct(USER_ID, "0.900000", saveProduct("커서", ProductCategory.LIVING));
        PersonalProduct disliked =
                savePersonalProduct(USER_ID, "0.800000", saveProduct("별로예요", ProductCategory.LIVING));
        savePersonalProduct(USER_ID, "0.700000", saveProduct("다른 카테고리", ProductCategory.BEAUTY));
        PersonalProduct next = savePersonalProduct(USER_ID, "0.600000", saveProduct("다음", ProductCategory.LIVING));
        disliked.delete();
        entityManager.flush();
        entityManager.clear();

        List<PersonalProductSummary> result = personalProductRepository.findAllByUserIdAndPriceRangeAfterCursor(
                USER_ID, MIN_PRICE, MAX_PRICE, ProductCategory.LIVING,
                new BigDecimal("0.900000"), cursorItem.getId(), Limit.of(10));

        assertThat(result).extracting(PersonalProductSummary::id).containsExactly(next.getId());
    }

    @Test
    void feedbackSaved_findAllByUserIdAndPriceRange_returnsOnlyOwnPersonalFeedback() {
        Product liked = saveProduct("마음에 들어요");
        Product likedInGift = saveProduct("선물에서만 마음에 들어요");
        Product likedByOther = saveProduct("다른 사용자만 마음에 들어요");
        PersonalProduct likedItem = savePersonalProduct(USER_ID, "0.900000", liked);
        PersonalProduct likedInGiftItem = savePersonalProduct(USER_ID, "0.800000", likedInGift);
        PersonalProduct likedByOtherItem = savePersonalProduct(USER_ID, "0.700000", likedByOther);
        saveFeedback(USER_ID, liked, ProductContext.PERSONAL);
        saveFeedback(USER_ID, likedInGift, ProductContext.MY_GIFT);
        saveFeedback(OTHER_USER_ID, likedByOther, ProductContext.PERSONAL);
        entityManager.clear();

        List<PersonalProductSummary> result = personalProductRepository.findAllByUserIdAndPriceRange(
                USER_ID, MIN_PRICE, MAX_PRICE, null, Limit.of(10));

        assertThat(result).extracting(PersonalProductSummary::id, PersonalProductSummary::feedback)
                .containsExactly(
                        tuple(likedItem.getId(), ProductFeedbackType.LIKE),
                        tuple(likedInGiftItem.getId(), null),
                        tuple(likedByOtherItem.getId(), null));
    }

    private Product saveProduct(String name) {
        return saveProduct(name, "40000.00");
    }

    private Product saveProduct(String name, String price) {
        return entityManager.persist(new Product(
                PlatformType.COUPANG, name, name, null, null,
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

    private PersonalProduct savePersonalProduct(Long userId, String score, Product product) {
        return entityManager.persistAndFlush(new PersonalProduct(userId, product, new BigDecimal(score), null));
    }
}
