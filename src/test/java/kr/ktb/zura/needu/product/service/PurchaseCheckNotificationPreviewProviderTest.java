package kr.ktb.zura.needu.product.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import jakarta.persistence.EntityManager;
import kr.ktb.zura.needu.friend.service.FriendService;
import kr.ktb.zura.needu.notification.service.NotificationPreview;
import kr.ktb.zura.needu.product.entity.Product;
import kr.ktb.zura.needu.product.entity.ProductLinkClick;
import kr.ktb.zura.needu.product.entity.PurchaseCheck;
import kr.ktb.zura.needu.product.type.PlatformType;
import kr.ktb.zura.needu.product.type.ProductContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// 소유자 조건과 fetch join 조회를 실제 JPA(H2) 위에서 검증한다.
@DataJpaTest
@Import(PurchaseCheckNotificationPreviewProvider.class)
class PurchaseCheckNotificationPreviewProviderTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long FRIEND_USER_ID = 3L;

    @Autowired
    private PurchaseCheckNotificationPreviewProvider provider;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private FriendService friendService;

    private Product product;

    @BeforeEach
    void setUp() {
        product = new Product(
                PlatformType.COUPANG, "external-1", "섬유유연제", null, null,
                new BigDecimal("16500.00"), "https://example.com/product.jpg", null,
                "https://example.com/product");
        entityManager.persist(product);
    }

    @Test
    void ownPurchaseCheck_findAllPreviews_returnsProductAndContext() {
        PurchaseCheck purchaseCheck = savePurchaseCheck(USER_ID, ProductContext.PERSONAL, null);

        Map<Long, NotificationPreview> previews = provider.findAllPreviews(USER_ID, List.of(purchaseCheck.getId()));

        assertThat(previews).containsExactly(Map.entry(purchaseCheck.getId(), new PurchaseCheckNotificationPreview(
                product.getId(), "섬유유연제", "https://example.com/product.jpg",
                ProductContext.PERSONAL, null, null)));
    }

    @Test
    void otherUsersPurchaseCheck_findAllPreviews_excludesIt() {
        PurchaseCheck own = savePurchaseCheck(USER_ID, ProductContext.MY_GIFT, null);
        PurchaseCheck others = savePurchaseCheck(OTHER_USER_ID, ProductContext.PERSONAL, null);

        Map<Long, NotificationPreview> previews =
                provider.findAllPreviews(USER_ID, List.of(own.getId(), others.getId()));

        assertThat(previews).containsOnlyKeys(own.getId());
    }

    @Test
    void friendGiftWithConfirmedFriend_findAllPreviews_includesFriend() {
        PurchaseCheck purchaseCheck = savePurchaseCheck(USER_ID, ProductContext.FRIEND_GIFT, FRIEND_USER_ID);
        given(friendService.findAllFriendNicknames(USER_ID, List.of(FRIEND_USER_ID)))
                .willReturn(Map.of(FRIEND_USER_ID, "수연"));

        Map<Long, NotificationPreview> previews = provider.findAllPreviews(USER_ID, List.of(purchaseCheck.getId()));

        PurchaseCheckNotificationPreview preview = (PurchaseCheckNotificationPreview) previews.get(purchaseCheck.getId());
        assertThat(preview.context()).isEqualTo(ProductContext.FRIEND_GIFT);
        assertThat(preview.friendUserId()).isEqualTo(FRIEND_USER_ID);
        assertThat(preview.friendName()).isEqualTo("수연");
    }

    @Test
    void friendGiftWithoutConfirmedFriend_findAllPreviews_hidesFriendUserIdAndName() {
        PurchaseCheck purchaseCheck = savePurchaseCheck(USER_ID, ProductContext.FRIEND_GIFT, FRIEND_USER_ID);
        given(friendService.findAllFriendNicknames(USER_ID, List.of(FRIEND_USER_ID))).willReturn(Map.of());

        Map<Long, NotificationPreview> previews = provider.findAllPreviews(USER_ID, List.of(purchaseCheck.getId()));

        PurchaseCheckNotificationPreview preview = (PurchaseCheckNotificationPreview) previews.get(purchaseCheck.getId());
        assertThat(preview.context()).isEqualTo(ProductContext.FRIEND_GIFT);
        assertThat(preview.friendUserId()).isNull();
        assertThat(preview.friendName()).isNull();
    }

    @Test
    void emptyResourceIds_findAllPreviews_returnsEmptyWithoutFriendLookup() {
        assertThat(provider.findAllPreviews(USER_ID, List.of())).isEmpty();
        verify(friendService, never()).findAllFriendNicknames(any(), any());
    }

    private PurchaseCheck savePurchaseCheck(Long userId, ProductContext context, Long friendUserId) {
        ProductLinkClick click = new ProductLinkClick(userId, product, context, friendUserId);
        entityManager.persist(click);
        PurchaseCheck purchaseCheck = PurchaseCheck.create(click);
        entityManager.persist(purchaseCheck);
        entityManager.flush();
        entityManager.clear();
        return purchaseCheck;
    }
}
