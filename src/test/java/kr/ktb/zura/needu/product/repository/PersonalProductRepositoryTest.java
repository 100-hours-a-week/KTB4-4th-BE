package kr.ktb.zura.needu.product.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import kr.ktb.zura.needu.product.entity.PersonalProduct;
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
class PersonalProductRepositoryTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;

    @Autowired
    private PersonalProductRepository personalProductRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void personalProductsSaved_findAllByUserId_returnsOnlyOwnItemsOrderedByScoreThenIdDesc() {
        PersonalProduct low = savePersonalProduct(USER_ID, "0.100000", saveProduct("낮은 점수"));
        PersonalProduct sameScoreFirst = savePersonalProduct(USER_ID, "0.500000", saveProduct("같은 점수 1"));
        PersonalProduct sameScoreSecond = savePersonalProduct(USER_ID, "0.500000", saveProduct("같은 점수 2"));
        PersonalProduct high = savePersonalProduct(USER_ID, "0.900000", saveProduct("높은 점수"));
        savePersonalProduct(OTHER_USER_ID, "0.990000", saveProduct("다른 사용자"));
        entityManager.clear();

        List<PersonalProduct> result = personalProductRepository.findAllByUserId(USER_ID, Limit.of(10));

        assertThat(result).extracting(PersonalProduct::getId)
                .containsExactly(high.getId(), sameScoreSecond.getId(), sameScoreFirst.getId(), low.getId());
    }

    @Test
    void limitGiven_findAllByUserId_returnsAtMostLimitItems() {
        savePersonalProduct(USER_ID, "0.100000", saveProduct("상품 1"));
        savePersonalProduct(USER_ID, "0.200000", saveProduct("상품 2"));
        savePersonalProduct(USER_ID, "0.300000", saveProduct("상품 3"));

        assertThat(personalProductRepository.findAllByUserId(USER_ID, Limit.of(2))).hasSize(2);
    }

    @Test
    void inactiveOrDeletedProduct_findAllByUserId_excludesItem() {
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

        List<PersonalProduct> result = personalProductRepository.findAllByUserId(USER_ID, Limit.of(10));

        assertThat(result).extracting(PersonalProduct::getId).containsExactly(active.getId());
    }

    @Test
    void cursorGiven_findAllByUserIdAfterCursor_returnsItemsAfterCursorPosition() {
        PersonalProduct low = savePersonalProduct(USER_ID, "0.100000", saveProduct("낮은 점수"));
        PersonalProduct sameScoreFirst = savePersonalProduct(USER_ID, "0.500000", saveProduct("같은 점수 1"));
        PersonalProduct sameScoreSecond = savePersonalProduct(USER_ID, "0.500000", saveProduct("같은 점수 2"));
        savePersonalProduct(USER_ID, "0.900000", saveProduct("높은 점수"));
        entityManager.clear();

        List<PersonalProduct> result = personalProductRepository.findAllByUserIdAfterCursor(
                USER_ID, new BigDecimal("0.500000"), sameScoreSecond.getId(), Limit.of(10));

        assertThat(result).extracting(PersonalProduct::getId).containsExactly(sameScoreFirst.getId(), low.getId());
    }

    @Test
    void personalProductsFound_findAllByUserId_fetchesProductTogether() {
        savePersonalProduct(USER_ID, "0.100000", saveProduct("램프"));
        entityManager.clear();

        PersonalProduct result = personalProductRepository.findAllByUserId(USER_ID, Limit.of(1)).getFirst();

        assertThat(entityManager.getEntityManager().getEntityManagerFactory().getPersistenceUnitUtil()
                .isLoaded(result, "product")).isTrue();
        assertThat(result.getProduct().getName()).isEqualTo("램프");
    }

    private Product saveProduct(String name) {
        return entityManager.persist(new Product(null, name, null, null, new BigDecimal("10000.00"), null, null, null));
    }

    private PersonalProduct savePersonalProduct(Long userId, String score, Product product) {
        return entityManager.persistAndFlush(new PersonalProduct(userId, product, new BigDecimal(score), null));
    }
}
