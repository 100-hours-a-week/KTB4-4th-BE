package kr.ktb.zura.needu.product.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
import org.springframework.data.domain.Limit;
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

    @Test
    void feedbacksSaved_findAllActivitiesByUserIdSince_returnsOwnActiveRecentFeedbacksNewestFirst() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 2, 7, 0);
        LocalDateTime since = now.minusDays(90);
        ProductFeedback liked = saveFeedback(USER_ID, saveProduct("좋아요"), ProductContext.PERSONAL,
                ProductFeedbackType.LIKE, now.minusDays(10));
        ProductFeedback disliked = saveFeedback(USER_ID, saveProduct("싫어요"), ProductContext.MY_GIFT,
                ProductFeedbackType.DISLIKE, now.minusDays(1));
        ProductFeedback canceled = saveFeedback(USER_ID, saveProduct("취소함"), ProductContext.PERSONAL,
                ProductFeedbackType.LIKE, now.minusDays(2));
        canceled.delete();
        productFeedbackRepository.saveAndFlush(canceled);
        moveUpdatedAt(canceled, now.minusDays(2));
        saveFeedback(USER_ID, saveProduct("오래됨"), ProductContext.PERSONAL,
                ProductFeedbackType.DISLIKE, since.minusSeconds(1));
        saveFeedback(2L, saveProduct("다른 사용자"), ProductContext.PERSONAL,
                ProductFeedbackType.DISLIKE, now.minusDays(1));
        entityManager.clear();

        List<ProductFeedbackActivity> result =
                productFeedbackRepository.findAllActivitiesByUserIdSince(USER_ID, since, Limit.of(10));

        assertThat(result).containsExactly(
                new ProductFeedbackActivity(PlatformType.COUPANG, "싫어요", ProductFeedbackType.DISLIKE,
                        ProductContext.MY_GIFT, disliked.getUpdatedAt()),
                new ProductFeedbackActivity(PlatformType.COUPANG, "좋아요", ProductFeedbackType.LIKE,
                        ProductContext.PERSONAL, liked.getUpdatedAt()));
    }

    @Test
    void moreThanLimit_findAllActivitiesByUserIdSince_returnsNewestWithinLimit() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 2, 7, 0);
        saveFeedback(USER_ID, saveProduct("첫째"), ProductContext.PERSONAL, ProductFeedbackType.LIKE, now.minusDays(3));
        saveFeedback(USER_ID, saveProduct("둘째"), ProductContext.PERSONAL, ProductFeedbackType.LIKE, now.minusDays(1));
        saveFeedback(USER_ID, saveProduct("셋째"), ProductContext.PERSONAL, ProductFeedbackType.LIKE, now.minusDays(2));
        entityManager.clear();

        List<ProductFeedbackActivity> result = productFeedbackRepository.findAllActivitiesByUserIdSince(
                USER_ID, now.minusDays(90), Limit.of(2));

        assertThat(result).extracting(ProductFeedbackActivity::externalId).containsExactly("둘째", "셋째");
    }

    private void saveFeedback(Long userId, Product product, ProductContext context, ProductFeedbackType feedback) {
        productFeedbackRepository.saveAndFlush(new ProductFeedback(userId, product, context, feedback));
    }

    // updatedAt은 저장 시각으로 자동 기록되므로, 기간과 정렬을 확인할 때만 직접 옮긴다
    private ProductFeedback saveFeedback(Long userId, Product product, ProductContext context,
                                         ProductFeedbackType feedback, LocalDateTime updatedAt) {
        ProductFeedback saved = productFeedbackRepository.saveAndFlush(
                new ProductFeedback(userId, product, context, feedback));
        return moveUpdatedAt(saved, updatedAt);
    }

    private ProductFeedback moveUpdatedAt(ProductFeedback feedback, LocalDateTime updatedAt) {
        entityManager.getEntityManager()
                .createQuery("update ProductFeedback pf set pf.updatedAt = :updatedAt where pf.id = :id")
                .setParameter("updatedAt", updatedAt)
                .setParameter("id", feedback.getId())
                .executeUpdate();
        entityManager.clear();
        return entityManager.find(ProductFeedback.class, feedback.getId());
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
