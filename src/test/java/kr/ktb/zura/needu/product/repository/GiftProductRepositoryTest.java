package kr.ktb.zura.needu.product.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import kr.ktb.zura.needu.product.entity.GiftProduct;
import kr.ktb.zura.needu.product.entity.Product;
import kr.ktb.zura.needu.product.type.ProductStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.Limit;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

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

        List<GiftProduct> result =
                giftProductRepository.findAllByUserIdAndPriceRange(USER_ID, MIN_PRICE, MAX_PRICE, Limit.of(10));

        assertThat(result).extracting(GiftProduct::getId)
                .containsExactly(high.getId(), sameScoreSecond.getId(), sameScoreFirst.getId(), low.getId());
    }

    @Test
    void productsOutsidePriceRange_findAllByUserIdAndPriceRange_returnsOnlyItemsWithinRangeInclusive() {
        GiftProduct minBoundary = saveGiftProduct(USER_ID, "0.100000", saveProduct("최소 경계", "30000.00"));
        GiftProduct maxBoundary = saveGiftProduct(USER_ID, "0.200000", saveProduct("최대 경계", "50000.00"));
        saveGiftProduct(USER_ID, "0.900000", saveProduct("최소 미만", "29999.00"));
        saveGiftProduct(USER_ID, "0.800000", saveProduct("최대 초과", "50001.00"));
        entityManager.clear();

        List<GiftProduct> result =
                giftProductRepository.findAllByUserIdAndPriceRange(USER_ID, MIN_PRICE, MAX_PRICE, Limit.of(10));

        assertThat(result).extracting(GiftProduct::getId).containsExactly(maxBoundary.getId(), minBoundary.getId());
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

        List<GiftProduct> result =
                giftProductRepository.findAllByUserIdAndPriceRange(USER_ID, MIN_PRICE, MAX_PRICE, Limit.of(10));

        assertThat(result).extracting(GiftProduct::getId).containsExactly(active.getId());
    }

    @Test
    void cursorGiven_findAllByUserIdAndPriceRangeAfterCursor_returnsItemsAfterCursorPosition() {
        GiftProduct low = saveGiftProduct(USER_ID, "0.100000", saveProduct("낮은 점수", "40000.00"));
        GiftProduct sameScoreFirst = saveGiftProduct(USER_ID, "0.500000", saveProduct("같은 점수 1", "40000.00"));
        GiftProduct sameScoreSecond = saveGiftProduct(USER_ID, "0.500000", saveProduct("같은 점수 2", "40000.00"));
        saveGiftProduct(USER_ID, "0.900000", saveProduct("높은 점수", "40000.00"));
        entityManager.clear();

        List<GiftProduct> result = giftProductRepository.findAllByUserIdAndPriceRangeAfterCursor(
                USER_ID, MIN_PRICE, MAX_PRICE, new BigDecimal("0.500000"), sameScoreSecond.getId(), Limit.of(10));

        assertThat(result).extracting(GiftProduct::getId).containsExactly(sameScoreFirst.getId(), low.getId());
    }

    @Test
    void giftProductsFound_findAllByUserIdAndPriceRange_fetchesProductAndKeywordsTogether() {
        saveGiftProduct(USER_ID, "0.100000", saveProduct("램프", "40000.00"));
        entityManager.clear();

        GiftProduct result = giftProductRepository
                .findAllByUserIdAndPriceRange(USER_ID, MIN_PRICE, MAX_PRICE, Limit.of(1)).getFirst();

        assertThat(entityManager.getEntityManager().getEntityManagerFactory().getPersistenceUnitUtil()
                .isLoaded(result, "product")).isTrue();
        assertThat(result.getProduct().getName()).isEqualTo("램프");
        assertThat(result.getTasteKeywords()).containsExactly("인테리어", "감성");
    }

    private Product saveProduct(String name, String price) {
        return entityManager.persist(new Product(null, name, "LIVING", null, new BigDecimal(price), null, null));
    }

    private GiftProduct saveGiftProduct(Long userId, String score, Product product) {
        return entityManager.persistAndFlush(new GiftProduct(
                userId, product, new BigDecimal(score), "이유", List.of("인테리어", "감성")));
    }
}
