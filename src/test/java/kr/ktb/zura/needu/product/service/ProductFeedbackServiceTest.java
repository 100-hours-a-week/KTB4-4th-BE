package kr.ktb.zura.needu.product.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import jakarta.persistence.EntityManager;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.CommonErrorCode;
import kr.ktb.zura.needu.product.dto.response.ProductFeedbackResponse;
import kr.ktb.zura.needu.product.entity.GiftProduct;
import kr.ktb.zura.needu.product.entity.PersonalProduct;
import kr.ktb.zura.needu.product.entity.Product;
import kr.ktb.zura.needu.product.entity.ProductFeedback;
import kr.ktb.zura.needu.product.exception.ProductErrorCode;
import kr.ktb.zura.needu.product.repository.ProductFeedbackRepository;
import kr.ktb.zura.needu.product.type.PlatformType;
import kr.ktb.zura.needu.product.type.ProductContext;
import kr.ktb.zura.needu.product.type.ProductFeedbackType;
import kr.ktb.zura.needu.user.exception.UserErrorCode;
import kr.ktb.zura.needu.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.willThrow;

// upsert·멱등성은 유일 제약과 실제 저장 결과로 확인해야 해서 실제 JPA(H2) 위에서 검증한다.
@DataJpaTest
@RecordApplicationEvents
@Import(ProductFeedbackService.class)
class ProductFeedbackServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;

    @Autowired
    private ProductFeedbackService productFeedbackService;

    @Autowired
    private ProductFeedbackRepository productFeedbackRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ApplicationEvents applicationEvents;

    @MockitoBean
    private UserService userService;

    @Test
    void noSavedFeedback_updateFeedback_createsFeedback() {
        Product lamp = savePersonalRecommendation(USER_ID);

        ProductFeedbackResponse response = productFeedbackService.updateFeedback(
                USER_ID, lamp.getId(), ProductContext.PERSONAL, ProductFeedbackType.LIKE);

        assertThat(response).isEqualTo(
                new ProductFeedbackResponse(lamp.getId(), ProductContext.PERSONAL, ProductFeedbackType.LIKE));
        assertThat(findAllFeedbacks()).singleElement().satisfies(feedback -> {
            assertThat(feedback.getUserId()).isEqualTo(USER_ID);
            assertThat(feedback.getProduct().getId()).isEqualTo(lamp.getId());
            assertThat(feedback.getContext()).isEqualTo(ProductContext.PERSONAL);
            assertThat(feedback.getFeedbackType()).isEqualTo(ProductFeedbackType.LIKE);
            assertThat(feedback.isDeleted()).isFalse();
        });
    }

    @Test
    void sameFeedbackRepeated_updateFeedback_keepsSingleRowWithSameResult() {
        Product lamp = savePersonalRecommendation(USER_ID);

        ProductFeedbackResponse first = productFeedbackService.updateFeedback(
                USER_ID, lamp.getId(), ProductContext.PERSONAL, ProductFeedbackType.LIKE);
        flushAndClear();
        ProductFeedbackResponse second = productFeedbackService.updateFeedback(
                USER_ID, lamp.getId(), ProductContext.PERSONAL, ProductFeedbackType.LIKE);
        flushAndClear();

        assertThat(second).isEqualTo(first);
        assertThat(findAllFeedbacks()).singleElement()
                .extracting(ProductFeedback::getFeedbackType).isEqualTo(ProductFeedbackType.LIKE);
    }

    @Test
    void myGiftRecommendation_updateFeedback_savesWithMyGiftContext() {
        Product mug = saveProduct("머그컵");
        entityManager.persist(new GiftProduct(USER_ID, mug, new BigDecimal("0.500000"), null, List.of()));

        productFeedbackService.updateFeedback(USER_ID, mug.getId(), ProductContext.MY_GIFT, ProductFeedbackType.LIKE);
        flushAndClear();

        assertThat(findAllFeedbacks()).singleElement()
                .extracting(ProductFeedback::getContext).isEqualTo(ProductContext.MY_GIFT);
    }

    @Test
    void dislike_updateFeedback_deletesOnlyRecommendationInSameContext() {
        Product lamp = saveProduct("램프");
        entityManager.persist(new PersonalProduct(USER_ID, lamp, new BigDecimal("0.500000"), null));
        entityManager.persist(new GiftProduct(USER_ID, lamp, new BigDecimal("0.500000"), null, List.of()));
        flushAndClear();

        productFeedbackService.updateFeedback(
                USER_ID, lamp.getId(), ProductContext.PERSONAL, ProductFeedbackType.DISLIKE);
        flushAndClear();

        assertThat(findPersonalProduct(USER_ID, lamp).isDeleted()).isTrue();
        assertThat(findGiftProduct(USER_ID, lamp).isDeleted()).isFalse();
        assertThat(entityManager.find(Product.class, lamp.getId()).getDeletedAt()).isNull();
        assertThat(applicationEvents.stream(ProductRecommendationsUpdatedEvent.class))
                .containsExactly(new ProductRecommendationsUpdatedEvent(USER_ID));
    }

    @Test
    void myGiftDislike_updateFeedback_deletesGiftRecommendation() {
        Product mug = saveProduct("머그컵");
        entityManager.persist(new GiftProduct(USER_ID, mug, new BigDecimal("0.500000"), null, List.of()));
        flushAndClear();

        productFeedbackService.updateFeedback(USER_ID, mug.getId(), ProductContext.MY_GIFT, ProductFeedbackType.DISLIKE);
        flushAndClear();

        assertThat(findGiftProduct(USER_ID, mug).isDeleted()).isTrue();
    }

    @Test
    void dislikeRepeated_updateFeedback_keepsRecommendationDeleted() {
        Product lamp = savePersonalRecommendation(USER_ID);

        productFeedbackService.updateFeedback(
                USER_ID, lamp.getId(), ProductContext.PERSONAL, ProductFeedbackType.DISLIKE);
        flushAndClear();
        ProductFeedbackResponse second = productFeedbackService.updateFeedback(
                USER_ID, lamp.getId(), ProductContext.PERSONAL, ProductFeedbackType.DISLIKE);
        flushAndClear();

        assertThat(second.feedback()).isEqualTo(ProductFeedbackType.DISLIKE);
        assertThat(findPersonalProduct(USER_ID, lamp).isDeleted()).isTrue();
        assertThat(findAllFeedbacks()).singleElement()
                .extracting(ProductFeedback::getFeedbackType).isEqualTo(ProductFeedbackType.DISLIKE);
    }

    @Test
    void likeAfterDislike_updateFeedback_throwsAlreadyDislikedAndKeepsDislike() {
        Product lamp = savePersonalRecommendation(USER_ID);
        productFeedbackService.updateFeedback(
                USER_ID, lamp.getId(), ProductContext.PERSONAL, ProductFeedbackType.DISLIKE);
        flushAndClear();

        assertThatThrownBy(() -> productFeedbackService.updateFeedback(
                USER_ID, lamp.getId(), ProductContext.PERSONAL, ProductFeedbackType.LIKE))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ProductErrorCode.PRODUCT_FEEDBACK_ALREADY_DISLIKED);
        entityManager.clear();

        assertThat(findPersonalProduct(USER_ID, lamp).isDeleted()).isTrue();
        assertThat(findAllFeedbacks()).singleElement()
                .extracting(ProductFeedback::getFeedbackType).isEqualTo(ProductFeedbackType.DISLIKE);
    }

    @Test
    void dislikeAfterLike_updateFeedback_deletesRecommendation() {
        Product lamp = savePersonalRecommendation(USER_ID);
        productFeedbackService.updateFeedback(USER_ID, lamp.getId(), ProductContext.PERSONAL, ProductFeedbackType.LIKE);
        flushAndClear();

        productFeedbackService.updateFeedback(
                USER_ID, lamp.getId(), ProductContext.PERSONAL, ProductFeedbackType.DISLIKE);
        flushAndClear();

        assertThat(findPersonalProduct(USER_ID, lamp).isDeleted()).isTrue();
        assertThat(findAllFeedbacks()).singleElement()
                .extracting(ProductFeedback::getFeedbackType).isEqualTo(ProductFeedbackType.DISLIKE);
    }

    @Test
    void like_updateFeedback_keepsRecommendationWithoutEvent() {
        Product lamp = savePersonalRecommendation(USER_ID);

        productFeedbackService.updateFeedback(USER_ID, lamp.getId(), ProductContext.PERSONAL, ProductFeedbackType.LIKE);
        flushAndClear();

        assertThat(findPersonalProduct(USER_ID, lamp).isDeleted()).isFalse();
        assertThat(applicationEvents.stream(ProductRecommendationsUpdatedEvent.class)).isEmpty();
    }

    @Test
    void productOnlyInOtherContext_updateFeedback_throwsRecommendationNotFound() {
        Product lamp = savePersonalRecommendation(USER_ID);

        assertThatThrownBy(() -> productFeedbackService.updateFeedback(
                USER_ID, lamp.getId(), ProductContext.MY_GIFT, ProductFeedbackType.LIKE))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ProductErrorCode.PRODUCT_RECOMMENDATION_NOT_FOUND);
        assertThat(findAllFeedbacks()).isEmpty();
    }

    @Test
    void otherUsersRecommendation_updateFeedback_throwsRecommendationNotFound() {
        Product lamp = savePersonalRecommendation(OTHER_USER_ID);

        assertThatThrownBy(() -> productFeedbackService.updateFeedback(
                USER_ID, lamp.getId(), ProductContext.PERSONAL, ProductFeedbackType.LIKE))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ProductErrorCode.PRODUCT_RECOMMENDATION_NOT_FOUND);
    }

    @Test
    void deletedProduct_updateFeedback_throwsRecommendationNotFound() {
        Product lamp = savePersonalRecommendation(USER_ID);
        // Product에는 삭제 도메인 메서드가 아직 없어 테스트에서만 직접 설정한다.
        ReflectionTestUtils.setField(entityManager.find(Product.class, lamp.getId()), "deletedAt", LocalDateTime.now());
        flushAndClear();

        assertThatThrownBy(() -> productFeedbackService.updateFeedback(
                USER_ID, lamp.getId(), ProductContext.PERSONAL, ProductFeedbackType.LIKE))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ProductErrorCode.PRODUCT_RECOMMENDATION_NOT_FOUND);
    }

    @Test
    void friendGiftContext_updateFeedback_throwsInvalidInput() {
        Product lamp = savePersonalRecommendation(USER_ID);

        assertThatThrownBy(() -> productFeedbackService.updateFeedback(
                USER_ID, lamp.getId(), ProductContext.FRIEND_GIFT, ProductFeedbackType.LIKE))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.COMMON_INVALID_INPUT);
    }

    @Test
    void inactiveUser_updateFeedback_throwsUserErrorWithoutSaving() {
        Product lamp = savePersonalRecommendation(USER_ID);
        willThrow(new BusinessException(UserErrorCode.USER_BLOCKED)).given(userService).validateActiveUser(USER_ID);

        assertThatThrownBy(() -> productFeedbackService.updateFeedback(
                USER_ID, lamp.getId(), ProductContext.PERSONAL, ProductFeedbackType.LIKE))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.USER_BLOCKED);
        assertThat(findAllFeedbacks()).isEmpty();
    }

    private Product savePersonalRecommendation(Long userId) {
        Product product = saveProduct("램프");
        entityManager.persist(new PersonalProduct(userId, product, new BigDecimal("0.500000"), null));
        flushAndClear();
        return product;
    }

    private Product saveProduct(String name) {
        Product product = new Product(
                PlatformType.COUPANG, name, name, null, null,
                new BigDecimal("40000.00"), null, null, null);
        entityManager.persist(product);
        return product;
    }

    private PersonalProduct findPersonalProduct(Long userId, Product product) {
        return entityManager.createQuery("""
                        select pp from PersonalProduct pp
                        where pp.userId = :userId and pp.product.id = :productId""", PersonalProduct.class)
                .setParameter("userId", userId)
                .setParameter("productId", product.getId())
                .getSingleResult();
    }

    private GiftProduct findGiftProduct(Long userId, Product product) {
        return entityManager.createQuery("""
                        select gp from GiftProduct gp
                        where gp.userId = :userId and gp.product.id = :productId""", GiftProduct.class)
                .setParameter("userId", userId)
                .setParameter("productId", product.getId())
                .getSingleResult();
    }

    private List<ProductFeedback> findAllFeedbacks() {
        entityManager.flush();
        return productFeedbackRepository.findAll();
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
