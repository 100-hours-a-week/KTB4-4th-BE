package kr.ktb.zura.needu.product.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import kr.ktb.zura.needu.product.entity.Product;
import kr.ktb.zura.needu.product.entity.ProductLinkClick;
import kr.ktb.zura.needu.product.entity.PurchaseCheck;
import kr.ktb.zura.needu.product.type.PlatformType;
import kr.ktb.zura.needu.product.type.ProductContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.Limit;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class PurchaseCheckRepositoryTest {

    private static final Long USER_ID = 1L;
    private static final Long FRIEND_ID = 2L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 2, 7, 0);
    private static final LocalDateTime SINCE = NOW.minusDays(90);

    @Autowired
    private PurchaseCheckRepository purchaseCheckRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void purchasesRelatedToUser_findAllPurchasedActivitiesRelatedToUserIdSince_returnsOwnAndReceivedNewestFirst() {
        savePurchaseCheck(USER_ID, saveProduct("내 추천 구매"), ProductContext.PERSONAL, null, true, NOW.minusDays(5));
        savePurchaseCheck(USER_ID, saveProduct("내 선물 목록 구매"), ProductContext.MY_GIFT, null, true, NOW.minusDays(1));
        savePurchaseCheck(FRIEND_ID, saveProduct("친구가 사 줌"), ProductContext.FRIEND_GIFT, USER_ID, true,
                NOW.minusDays(3));

        List<PurchaseActivity> result = purchaseCheckRepository
                .findAllPurchasedActivitiesRelatedToUserIdSince(USER_ID, SINCE, Limit.of(10));

        assertThat(result).containsExactly(
                new PurchaseActivity(PlatformType.COUPANG, "내 선물 목록 구매", ProductContext.MY_GIFT, NOW.minusDays(1)),
                new PurchaseActivity(PlatformType.COUPANG, "친구가 사 줌", ProductContext.FRIEND_GIFT, NOW.minusDays(3)),
                new PurchaseActivity(PlatformType.COUPANG, "내 추천 구매", ProductContext.PERSONAL, NOW.minusDays(5)));
    }

    // 내가 친구 선물로 산 것은 친구의 물건이라 내 기록으로 보내지 않는다
    @Test
    void giftBoughtForFriend_findAllPurchasedActivitiesRelatedToUserIdSince_excludesIt() {
        savePurchaseCheck(USER_ID, saveProduct("친구 선물로 삼"), ProductContext.FRIEND_GIFT, FRIEND_ID, true,
                NOW.minusDays(1));

        assertThat(purchaseCheckRepository
                .findAllPurchasedActivitiesRelatedToUserIdSince(USER_ID, SINCE, Limit.of(10))).isEmpty();
    }

    @Test
    void notPurchasedOrUnanswered_findAllPurchasedActivitiesRelatedToUserIdSince_excludesThem() {
        savePurchaseCheck(USER_ID, saveProduct("안 삼"), ProductContext.PERSONAL, null, false, NOW.minusDays(1));
        ProductLinkClick unanswered = saveClick(USER_ID, saveProduct("답 안 함"), ProductContext.PERSONAL, null);
        purchaseCheckRepository.saveAndFlush(
                PurchaseCheck.create(entityManager.find(ProductLinkClick.class, unanswered.getId())));
        entityManager.clear();

        assertThat(purchaseCheckRepository
                .findAllPurchasedActivitiesRelatedToUserIdSince(USER_ID, SINCE, Limit.of(10))).isEmpty();
    }

    @Test
    void deletedClickOrOldAnswer_findAllPurchasedActivitiesRelatedToUserIdSince_excludesThem() {
        PurchaseCheck deletedClickCheck = savePurchaseCheck(
                USER_ID, saveProduct("삭제된 클릭"), ProductContext.PERSONAL, null, true, NOW.minusDays(1));
        ProductLinkClick deletedClick = entityManager.find(
                ProductLinkClick.class, deletedClickCheck.getProductLinkClick().getId());
        deletedClick.delete();
        entityManager.flush();
        savePurchaseCheck(USER_ID, saveProduct("오래된 구매"), ProductContext.PERSONAL, null, true,
                SINCE.minusSeconds(1));

        assertThat(purchaseCheckRepository
                .findAllPurchasedActivitiesRelatedToUserIdSince(USER_ID, SINCE, Limit.of(10))).isEmpty();
    }

    @Test
    void moreThanLimit_findAllPurchasedActivitiesRelatedToUserIdSince_returnsNewestWithinLimit() {
        savePurchaseCheck(USER_ID, saveProduct("첫째"), ProductContext.PERSONAL, null, true, NOW.minusDays(3));
        savePurchaseCheck(USER_ID, saveProduct("둘째"), ProductContext.PERSONAL, null, true, NOW.minusDays(1));
        savePurchaseCheck(USER_ID, saveProduct("셋째"), ProductContext.PERSONAL, null, true, NOW.minusDays(2));

        List<PurchaseActivity> result = purchaseCheckRepository
                .findAllPurchasedActivitiesRelatedToUserIdSince(USER_ID, SINCE, Limit.of(2));

        assertThat(result).extracting(PurchaseActivity::externalId).containsExactly("둘째", "셋째");
    }

    // answeredAt은 답한 시각으로 자동 기록되므로, 기간과 정렬을 확인할 때만 직접 옮긴다
    private PurchaseCheck savePurchaseCheck(Long buyerId, Product product, ProductContext context,
                                            Long friendUserId, boolean purchased, LocalDateTime answeredAt) {
        ProductLinkClick click = saveClick(buyerId, product, context, friendUserId);
        PurchaseCheck check = PurchaseCheck.create(entityManager.find(ProductLinkClick.class, click.getId()));
        check.answer(purchased);
        purchaseCheckRepository.saveAndFlush(check);
        entityManager.getEntityManager()
                .createQuery("update PurchaseCheck pc set pc.answeredAt = :answeredAt where pc.id = :id")
                .setParameter("answeredAt", answeredAt)
                .setParameter("id", check.getId())
                .executeUpdate();
        entityManager.clear();
        return entityManager.find(PurchaseCheck.class, check.getId());
    }

    private ProductLinkClick saveClick(Long userId, Product product, ProductContext context, Long friendUserId) {
        return entityManager.persistAndFlush(new ProductLinkClick(userId, product, context, friendUserId));
    }

    private Product saveProduct(String name) {
        return entityManager.persist(new Product(
                PlatformType.COUPANG, name, name, null, null,
                new BigDecimal("40000.00"), null, null, null));
    }
}
