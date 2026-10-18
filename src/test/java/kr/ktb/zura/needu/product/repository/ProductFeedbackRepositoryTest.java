package kr.ktb.zura.needu.product.repository;

import java.math.BigDecimal;
import java.util.List;

import kr.ktb.zura.needu.product.entity.Product;
import kr.ktb.zura.needu.product.entity.ProductFeedback;
import kr.ktb.zura.needu.product.type.PlatformType;
import kr.ktb.zura.needu.product.type.ProductContext;
import kr.ktb.zura.needu.product.type.ProductFeedbackType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class ProductFeedbackRepositoryTest {

    private static final Long USER_ID = 1L;

    @Autowired
    private ProductFeedbackRepository productFeedbackRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void sameUserProductContext_saveAndFlush_throwsDataIntegrityViolation() {
        Product lamp = saveProduct();
        productFeedbackRepository.saveAndFlush(
                new ProductFeedback(USER_ID, lamp, ProductContext.PERSONAL, ProductFeedbackType.LIKE));

        assertThatThrownBy(() -> productFeedbackRepository.saveAndFlush(
                new ProductFeedback(USER_ID, lamp, ProductContext.PERSONAL, ProductFeedbackType.DISLIKE)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sameProductDifferentContext_saveAndFlush_savesBoth() {
        Product lamp = saveProduct();
        productFeedbackRepository.saveAndFlush(
                new ProductFeedback(USER_ID, lamp, ProductContext.PERSONAL, ProductFeedbackType.LIKE));
        productFeedbackRepository.saveAndFlush(
                new ProductFeedback(USER_ID, lamp, ProductContext.MY_GIFT, ProductFeedbackType.DISLIKE));

        assertThat(productFeedbackRepository.count()).isEqualTo(2);
    }

    @Test
    void savedFeedback_findByUserIdAndProductIdAndContext_returnsOnlySameContext() {
        Product lamp = saveProduct();
        productFeedbackRepository.saveAndFlush(
                new ProductFeedback(USER_ID, lamp, ProductContext.PERSONAL, ProductFeedbackType.LIKE));
        entityManager.clear();

        assertThat(productFeedbackRepository
                .findByUserIdAndProductIdAndContext(USER_ID, lamp.getId(), ProductContext.PERSONAL))
                .hasValueSatisfying(found -> assertThat(found.getFeedbackType()).isEqualTo(ProductFeedbackType.LIKE));
        assertThat(productFeedbackRepository
                .findByUserIdAndProductIdAndContext(USER_ID, lamp.getId(), ProductContext.MY_GIFT))
                .isEmpty();
    }

    @Test
    void dislikeInAnyContext_existsByUserIdAndProductIdAndFeedbackType_returnsTrue() {
        Product lamp = saveProduct();
        productFeedbackRepository.saveAndFlush(
                new ProductFeedback(USER_ID, lamp, ProductContext.MY_GIFT, ProductFeedbackType.DISLIKE));
        entityManager.clear();

        assertThat(productFeedbackRepository.existsByUserIdAndProductIdAndFeedbackType(
                USER_ID, lamp.getId(), ProductFeedbackType.DISLIKE)).isTrue();
        assertThat(productFeedbackRepository.existsByUserIdAndProductIdAndFeedbackType(
                USER_ID, lamp.getId(), ProductFeedbackType.LIKE)).isFalse();
        assertThat(productFeedbackRepository.existsByUserIdAndProductIdAndFeedbackType(
                2L, lamp.getId(), ProductFeedbackType.DISLIKE)).isFalse();
    }

    @Test
    void dislikesSaved_findAllDislikedProductIds_returnsOnlyOwnDislikedAmongGivenProducts() {
        Product disliked = saveProduct("별로예요");
        Product dislikedTwice = saveProduct("두 번 별로예요");
        Product liked = saveProduct("마음에 들어요");
        Product notRequested = saveProduct("조회 대상 아님");
        saveFeedback(USER_ID, disliked, ProductContext.PERSONAL, ProductFeedbackType.DISLIKE);
        saveFeedback(USER_ID, dislikedTwice, ProductContext.PERSONAL, ProductFeedbackType.DISLIKE);
        saveFeedback(USER_ID, dislikedTwice, ProductContext.MY_GIFT, ProductFeedbackType.DISLIKE);
        saveFeedback(USER_ID, liked, ProductContext.PERSONAL, ProductFeedbackType.LIKE);
        saveFeedback(USER_ID, notRequested, ProductContext.PERSONAL, ProductFeedbackType.DISLIKE);
        saveFeedback(2L, liked, ProductContext.PERSONAL, ProductFeedbackType.DISLIKE);
        entityManager.clear();

        List<Long> result = productFeedbackRepository.findAllDislikedProductIds(
                USER_ID, List.of(disliked.getId(), dislikedTwice.getId(), liked.getId()));

        assertThat(result).containsExactlyInAnyOrder(disliked.getId(), dislikedTwice.getId());
    }

    private void saveFeedback(Long userId, Product product, ProductContext context, ProductFeedbackType feedback) {
        productFeedbackRepository.saveAndFlush(new ProductFeedback(userId, product, context, feedback));
    }

    private Product saveProduct() {
        return saveProduct("램프");
    }

    private Product saveProduct(String name) {
        return entityManager.persist(new Product(
                PlatformType.COUPANG, name, name, null, null,
                new BigDecimal("40000.00"), null, null, null));
    }
}
