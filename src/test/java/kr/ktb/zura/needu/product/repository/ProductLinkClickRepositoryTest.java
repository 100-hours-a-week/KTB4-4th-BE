package kr.ktb.zura.needu.product.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import kr.ktb.zura.needu.product.entity.Product;
import kr.ktb.zura.needu.product.entity.ProductLinkClick;
import kr.ktb.zura.needu.product.entity.PurchaseCheck;
import kr.ktb.zura.needu.product.type.PlatformType;
import kr.ktb.zura.needu.product.type.ProductContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Limit;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class ProductLinkClickRepositoryTest {

    private static final Long USER_ID = 1L;

    @Autowired
    private ProductLinkClickRepository productLinkClickRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void clickAfterSince_existsRecentClick_returnsTrueOnlyForSameUserProductContext() {
        Product lamp = saveProduct("램프");
        Product mug = saveProduct("머그컵");
        saveClick(USER_ID, lamp, ProductContext.PERSONAL, LocalDateTime.now().minusDays(1));
        LocalDateTime since = LocalDateTime.now().minusDays(7);

        assertThat(productLinkClickRepository.existsRecentClick(USER_ID, lamp.getId(), ProductContext.PERSONAL, since))
                .isTrue();
        assertThat(productLinkClickRepository.existsRecentClick(USER_ID, lamp.getId(), ProductContext.MY_GIFT, since))
                .isFalse();
        assertThat(productLinkClickRepository.existsRecentClick(USER_ID, mug.getId(), ProductContext.PERSONAL, since))
                .isFalse();
        assertThat(productLinkClickRepository.existsRecentClick(2L, lamp.getId(), ProductContext.PERSONAL, since))
                .isFalse();
    }

    @Test
    void clickBeforeSince_existsRecentClick_returnsFalse() {
        Product lamp = saveProduct("램프");
        saveClick(USER_ID, lamp, ProductContext.PERSONAL, LocalDateTime.now().minusDays(8));

        assertThat(productLinkClickRepository.existsRecentClick(
                USER_ID, lamp.getId(), ProductContext.PERSONAL, LocalDateTime.now().minusDays(7))).isFalse();
    }

    @Test
    void deletedClick_existsRecentClick_returnsFalse() {
        Product lamp = saveProduct("램프");
        ProductLinkClick click = saveClick(USER_ID, lamp, ProductContext.PERSONAL, LocalDateTime.now().minusDays(1));
        ProductLinkClick managed = entityManager.find(ProductLinkClick.class, click.getId());
        managed.delete();
        entityManager.flush();
        entityManager.clear();

        assertThat(productLinkClickRepository.existsRecentClick(
                USER_ID, lamp.getId(), ProductContext.PERSONAL, LocalDateTime.now().minusDays(7))).isFalse();
    }

    @Test
    void clicksInWindow_findPurchaseCheckTargetIds_returnsOnlyClicksWithoutPurchaseCheck() {
        Product lamp = saveProduct("램프");
        LocalDateTime to = LocalDateTime.now().minusDays(7);
        LocalDateTime from = to.minusDays(3);
        ProductLinkClick target = saveClick(USER_ID, lamp, ProductContext.PERSONAL, to.minusDays(1));
        ProductLinkClick checked = saveClick(USER_ID, lamp, ProductContext.MY_GIFT, to.minusDays(1));
        entityManager.persistAndFlush(PurchaseCheck.create(
                entityManager.find(ProductLinkClick.class, checked.getId())));
        ProductLinkClick deleted = saveClick(2L, lamp, ProductContext.PERSONAL, to.minusDays(1));
        entityManager.find(ProductLinkClick.class, deleted.getId()).delete();
        entityManager.flush();
        entityManager.clear();

        assertThat(productLinkClickRepository.findPurchaseCheckTargetIds(from, to, 0L, Limit.of(10)))
                .containsExactly(target.getId());
    }

    @Test
    void clicksOutsideWindow_findPurchaseCheckTargetIds_excludesTooRecentAndTooOldClicks() {
        Product lamp = saveProduct("램프");
        LocalDateTime to = LocalDateTime.now().minusDays(7);
        LocalDateTime from = to.minusDays(3);
        ProductLinkClick atTo = saveClick(USER_ID, lamp, ProductContext.PERSONAL, to);
        saveClick(USER_ID, lamp, ProductContext.MY_GIFT, to.plusSeconds(1));
        saveClick(USER_ID, lamp, ProductContext.FRIEND_GIFT, from);

        assertThat(productLinkClickRepository.findPurchaseCheckTargetIds(from, to, 0L, Limit.of(10)))
                .containsExactly(atTo.getId());
    }

    @Test
    void lastIdAndLimit_findPurchaseCheckTargetIds_returnsNextClicksInIdOrder() {
        Product lamp = saveProduct("램프");
        LocalDateTime to = LocalDateTime.now().minusDays(7);
        LocalDateTime from = to.minusDays(3);
        ProductLinkClick first = saveClick(USER_ID, lamp, ProductContext.PERSONAL, to.minusHours(1));
        ProductLinkClick second = saveClick(2L, lamp, ProductContext.PERSONAL, to.minusHours(3));
        ProductLinkClick third = saveClick(3L, lamp, ProductContext.PERSONAL, to.minusHours(2));

        assertThat(productLinkClickRepository.findPurchaseCheckTargetIds(from, to, 0L, Limit.of(2)))
                .containsExactly(first.getId(), second.getId());
        assertThat(productLinkClickRepository.findPurchaseCheckTargetIds(from, to, second.getId(), Limit.of(2)))
                .containsExactly(third.getId());
    }

    private Product saveProduct(String name) {
        return entityManager.persist(new Product(
                PlatformType.COUPANG, name, name, null, null,
                new BigDecimal("40000.00"), null, null, null));
    }

    // createdAt은 생성 시각으로 고정되어 수정할 수 없으므로, 기간 경계를 만들 때만 직접 옮긴다
    private ProductLinkClick saveClick(Long userId, Product product, ProductContext context, LocalDateTime createdAt) {
        ProductLinkClick click = entityManager.persistAndFlush(new ProductLinkClick(userId, product, context, null));
        entityManager.getEntityManager()
                .createQuery("update ProductLinkClick plc set plc.createdAt = :createdAt where plc.id = :id")
                .setParameter("createdAt", createdAt)
                .setParameter("id", click.getId())
                .executeUpdate();
        entityManager.clear();
        return click;
    }
}
