package kr.ktb.zura.needu.product.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import kr.ktb.zura.needu.product.entity.PersonalProduct;
import kr.ktb.zura.needu.product.entity.Product;
import kr.ktb.zura.needu.product.type.PlatformType;
import kr.ktb.zura.needu.product.type.ProductStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.Limit;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

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

        List<PersonalProduct> result =
                personalProductRepository.findAllByUserIdAndPriceRange(USER_ID, MIN_PRICE, MAX_PRICE, Limit.of(10));

        assertThat(result).extracting(PersonalProduct::getId)
                .containsExactly(high.getId(), sameScoreSecond.getId(), sameScoreFirst.getId(), low.getId());
    }

    @Test
    void limitGiven_findAllByUserIdAndPriceRange_returnsAtMostLimitItems() {
        savePersonalProduct(USER_ID, "0.100000", saveProduct("상품 1"));
        savePersonalProduct(USER_ID, "0.200000", saveProduct("상품 2"));
        savePersonalProduct(USER_ID, "0.300000", saveProduct("상품 3"));

        assertThat(personalProductRepository
                .findAllByUserIdAndPriceRange(USER_ID, MIN_PRICE, MAX_PRICE, Limit.of(2))).hasSize(2);
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

        List<PersonalProduct> result =
                personalProductRepository.findAllByUserIdAndPriceRange(USER_ID, MIN_PRICE, MAX_PRICE, Limit.of(10));

        assertThat(result).extracting(PersonalProduct::getId)
                .containsExactly(maxBoundary.getId(), minBoundary.getId());
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

        List<PersonalProduct> result =
                personalProductRepository.findAllByUserIdAndPriceRange(USER_ID, MIN_PRICE, MAX_PRICE, Limit.of(10));

        assertThat(result).extracting(PersonalProduct::getId).containsExactly(active.getId());
    }

    @Test
    void cursorGiven_findAllByUserIdAndPriceRangeAfterCursor_returnsItemsAfterCursorPosition() {
        PersonalProduct low = savePersonalProduct(USER_ID, "0.100000", saveProduct("낮은 점수"));
        PersonalProduct sameScoreFirst = savePersonalProduct(USER_ID, "0.500000", saveProduct("같은 점수 1"));
        PersonalProduct sameScoreSecond = savePersonalProduct(USER_ID, "0.500000", saveProduct("같은 점수 2"));
        savePersonalProduct(USER_ID, "0.900000", saveProduct("높은 점수"));
        entityManager.clear();

        List<PersonalProduct> result = personalProductRepository.findAllByUserIdAndPriceRangeAfterCursor(
                USER_ID, MIN_PRICE, MAX_PRICE,
                new BigDecimal("0.500000"), sameScoreSecond.getId(), Limit.of(10));

        assertThat(result).extracting(PersonalProduct::getId).containsExactly(sameScoreFirst.getId(), low.getId());
    }

    @Test
    void personalProductsFound_findAllByUserIdAndPriceRange_fetchesProductTogether() {
        savePersonalProduct(USER_ID, "0.100000", saveProduct("램프"));
        entityManager.clear();

        PersonalProduct result = personalProductRepository
                .findAllByUserIdAndPriceRange(USER_ID, MIN_PRICE, MAX_PRICE, Limit.of(1)).getFirst();

        assertThat(entityManager.getEntityManager().getEntityManagerFactory().getPersistenceUnitUtil()
                .isLoaded(result, "product")).isTrue();
        assertThat(result.getProduct().getName()).isEqualTo("램프");
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

    private Product saveProduct(String name) {
        return saveProduct(name, "40000.00");
    }

    private Product saveProduct(String name, String price) {
        return entityManager.persist(new Product(
                PlatformType.COUPANG, name, name, null, null,
                new BigDecimal(price), null, null, null));
    }

    private PersonalProduct savePersonalProduct(Long userId, String score, Product product) {
        return entityManager.persistAndFlush(new PersonalProduct(userId, product, new BigDecimal(score), null));
    }
}
