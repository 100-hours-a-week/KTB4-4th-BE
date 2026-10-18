package kr.ktb.zura.needu.product.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.persistence.EntityManager;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.friend.service.FriendService;
import kr.ktb.zura.needu.notification.service.NotificationCreationService;
import kr.ktb.zura.needu.notification.type.NotificationResourceType;
import kr.ktb.zura.needu.notification.type.NotificationType;
import kr.ktb.zura.needu.product.dto.response.PurchaseCheckDetailResponse;
import kr.ktb.zura.needu.product.dto.response.PurchaseCheckFriendResponse;
import kr.ktb.zura.needu.product.dto.response.PurchaseCheckResponse;
import kr.ktb.zura.needu.product.entity.Product;
import kr.ktb.zura.needu.product.entity.ProductLinkClick;
import kr.ktb.zura.needu.product.entity.PurchaseCheck;
import kr.ktb.zura.needu.product.exception.ProductErrorCode;
import kr.ktb.zura.needu.product.repository.PurchaseCheckRepository;
import kr.ktb.zura.needu.product.type.PlatformType;
import kr.ktb.zura.needu.product.type.ProductContext;
import kr.ktb.zura.needu.product.type.ProductStatus;
import kr.ktb.zura.needu.user.exception.UserErrorCode;
import kr.ktb.zura.needu.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// 소유자 조건 조회, @Version 충돌, 유니크 제약은 실제 저장 결과로 확인해야 해서 실제 JPA(H2) 위에서 검증한다.
@DataJpaTest
@Import(PurchaseCheckService.class)
// business-policy.yml 값이 바뀌어도 만료 시각 검증이 흔들리지 않도록 고정한다.
@TestPropertySource(properties = "needu.product.purchase-check.notification-expiration=7d")
class PurchaseCheckServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long FRIEND_USER_ID = 3L;

    @Autowired
    private PurchaseCheckService purchaseCheckService;

    @Autowired
    private PurchaseCheckRepository purchaseCheckRepository;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private FriendService friendService;

    @MockitoBean
    private NotificationCreationService notificationCreationService;

    private Product product;
    private ProductLinkClick click;

    @BeforeEach
    void setUp() {
        product = new Product(
                PlatformType.COUPANG, "external-1", "섬유유연제", null, null,
                new BigDecimal("16500.00"), "https://example.com/product.jpg", null,
                "https://example.com/product");
        entityManager.persist(product);
        click = new ProductLinkClick(USER_ID, product, ProductContext.PERSONAL, null);
        entityManager.persist(click);
        flushAndClear();
    }

    @Test
    void ownPurchaseCheck_findPurchaseCheck_returnsProductAndClickContext() {
        PurchaseCheck purchaseCheck = savePurchaseCheck();

        PurchaseCheckDetailResponse response = purchaseCheckService.findPurchaseCheck(USER_ID, purchaseCheck.getId());

        assertThat(response.purchaseCheckId()).isEqualTo(purchaseCheck.getId());
        assertThat(response.context()).isEqualTo(ProductContext.PERSONAL);
        assertThat(response.friend()).isNull();
        assertThat(response.product().productId()).isEqualTo(product.getId());
        assertThat(response.product().name()).isEqualTo("섬유유연제");
        assertThat(response.product().productImageUrl()).isEqualTo("https://example.com/product.jpg");
        assertThat(response.product().purchaseUrl()).isEqualTo("https://example.com/product");
        assertThat(response.product().price()).isEqualTo(16500L);
        assertThat(response.product().status()).isEqualTo(ProductStatus.ACTIVE);
        assertThat(response.answered()).isFalse();
        assertThat(response.purchased()).isNull();
        assertThat(response.answeredAt()).isNull();
        assertThat(response.clickedAt()).isNotNull();
        verify(friendService, never()).findAllFriendNicknames(anyLong(), any());
    }

    @Test
    void answeredPurchaseCheck_findPurchaseCheck_returnsAnswer() {
        PurchaseCheck purchaseCheck = savePurchaseCheck();
        purchaseCheckService.answerPurchaseCheck(USER_ID, purchaseCheck.getId(), false);
        flushAndClear();

        PurchaseCheckDetailResponse response = purchaseCheckService.findPurchaseCheck(USER_ID, purchaseCheck.getId());

        assertThat(response.answered()).isTrue();
        assertThat(response.purchased()).isFalse();
        assertThat(response.answeredAt()).isNotNull();
    }

    @Test
    void otherUsersPurchaseCheck_findPurchaseCheck_throwsNotFound() {
        PurchaseCheck purchaseCheck = savePurchaseCheck();

        assertThatThrownBy(() -> purchaseCheckService.findPurchaseCheck(OTHER_USER_ID, purchaseCheck.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ProductErrorCode.PURCHASE_CHECK_NOT_FOUND);
    }

    @Test
    void friendGiftClickWithConfirmedFriend_findPurchaseCheck_returnsFriend() {
        PurchaseCheck purchaseCheck = saveFriendGiftPurchaseCheck();
        given(friendService.findAllFriendNicknames(USER_ID, List.of(FRIEND_USER_ID)))
                .willReturn(Map.of(FRIEND_USER_ID, "수연"));

        PurchaseCheckDetailResponse response = purchaseCheckService.findPurchaseCheck(USER_ID, purchaseCheck.getId());

        assertThat(response.context()).isEqualTo(ProductContext.FRIEND_GIFT);
        assertThat(response.friend()).isEqualTo(new PurchaseCheckFriendResponse(FRIEND_USER_ID, "수연"));
    }

    @Test
    void friendGiftClickWithoutConfirmedFriend_findPurchaseCheck_returnsNullFriend() {
        PurchaseCheck purchaseCheck = saveFriendGiftPurchaseCheck();
        given(friendService.findAllFriendNicknames(USER_ID, List.of(FRIEND_USER_ID))).willReturn(Map.of());

        PurchaseCheckDetailResponse response = purchaseCheckService.findPurchaseCheck(USER_ID, purchaseCheck.getId());

        assertThat(response.context()).isEqualTo(ProductContext.FRIEND_GIFT);
        assertThat(response.friend()).isNull();
    }

    @Test
    void inactiveUser_findPurchaseCheck_throwsUserError() {
        PurchaseCheck purchaseCheck = savePurchaseCheck();
        willThrow(new BusinessException(UserErrorCode.USER_ONBOARDING_REQUIRED))
                .given(userService).validateActiveUser(USER_ID);

        assertThatThrownBy(() -> purchaseCheckService.findPurchaseCheck(USER_ID, purchaseCheck.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.USER_ONBOARDING_REQUIRED);
    }

    @Test
    void unansweredPurchaseCheck_answerPurchaseCheck_savesAnswer() {
        PurchaseCheck purchaseCheck = savePurchaseCheck();

        PurchaseCheckResponse response = purchaseCheckService.answerPurchaseCheck(
                USER_ID, purchaseCheck.getId(), true);
        flushAndClear();

        assertThat(response.purchaseCheckId()).isEqualTo(purchaseCheck.getId());
        assertThat(response.purchased()).isTrue();
        assertThat(response.answeredAt()).isNotNull();
        PurchaseCheck saved = purchaseCheckRepository.findById(purchaseCheck.getId()).orElseThrow();
        assertThat(saved.getPurchased()).isTrue();
        // DB에 저장된 answeredAt은 마이크로초까지만 남으므로 오차를 둔다
        assertThat(saved.getAnsweredAt()).isCloseTo(response.answeredAt(), within(1, ChronoUnit.MILLIS));
    }

    @Test
    void otherUsersPurchaseCheck_answerPurchaseCheck_throwsNotFound() {
        PurchaseCheck purchaseCheck = savePurchaseCheck();

        assertThatThrownBy(() -> purchaseCheckService.answerPurchaseCheck(OTHER_USER_ID, purchaseCheck.getId(), true))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ProductErrorCode.PURCHASE_CHECK_NOT_FOUND);
        flushAndClear();

        assertThat(purchaseCheckRepository.findById(purchaseCheck.getId()).orElseThrow().isAnswered()).isFalse();
    }

    @Test
    void missingPurchaseCheck_answerPurchaseCheck_throwsNotFound() {
        assertThatThrownBy(() -> purchaseCheckService.answerPurchaseCheck(USER_ID, 999L, true))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ProductErrorCode.PURCHASE_CHECK_NOT_FOUND);
    }

    @Test
    void answeredPurchaseCheck_answerPurchaseCheck_throwsAlreadyAnswered() {
        PurchaseCheck purchaseCheck = savePurchaseCheck();
        purchaseCheckService.answerPurchaseCheck(USER_ID, purchaseCheck.getId(), true);
        flushAndClear();

        assertThatThrownBy(() -> purchaseCheckService.answerPurchaseCheck(USER_ID, purchaseCheck.getId(), false))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ProductErrorCode.PURCHASE_CHECK_ALREADY_ANSWERED);
        flushAndClear();

        assertThat(purchaseCheckRepository.findById(purchaseCheck.getId()).orElseThrow().getPurchased()).isTrue();
    }

    @Test
    void concurrentAnswerCommittedFirst_answerPurchaseCheck_throwsAlreadyAnswered() {
        PurchaseCheck purchaseCheck = savePurchaseCheck();
        // 영속성 컨텍스트에 답하기 전 상태를 남겨 둔 채, 다른 요청의 답변 커밋처럼 DB의 version만 올린다
        PurchaseCheck staleCheck = purchaseCheckRepository.findById(purchaseCheck.getId()).orElseThrow();
        entityManager.createNativeQuery("""
                        update purchase_checks
                        set purchased = false, answered_at = current_timestamp, version = version + 1
                        where id = :id
                        """)
                .setParameter("id", staleCheck.getId())
                .executeUpdate();

        assertThatThrownBy(() -> purchaseCheckService.answerPurchaseCheck(USER_ID, staleCheck.getId(), true))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ProductErrorCode.PURCHASE_CHECK_ALREADY_ANSWERED);
    }

    @Test
    void inactiveUser_answerPurchaseCheck_doesNotSaveAnswer() {
        PurchaseCheck purchaseCheck = savePurchaseCheck();
        willThrow(new BusinessException(UserErrorCode.USER_ONBOARDING_REQUIRED))
                .given(userService).validateActiveUser(USER_ID);

        assertThatThrownBy(() -> purchaseCheckService.answerPurchaseCheck(USER_ID, purchaseCheck.getId(), true))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.USER_ONBOARDING_REQUIRED);
        flushAndClear();

        assertThat(purchaseCheckRepository.findById(purchaseCheck.getId()).orElseThrow().isAnswered()).isFalse();
    }

    @Test
    void clickWithoutPurchaseCheck_createPurchaseCheck_savesCheckAndSendsNotification() {
        ArgumentCaptor<LocalDateTime> expiresAt = ArgumentCaptor.forClass(LocalDateTime.class);

        purchaseCheckService.createPurchaseCheck(click.getId());
        flushAndClear();

        PurchaseCheck saved = purchaseCheckRepository.findAll().getFirst();
        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getProductLinkClick().getId()).isEqualTo(click.getId());
        assertThat(saved.isAnswered()).isFalse();
        verify(notificationCreationService).createNotifications(
                eq(Set.of(USER_ID)),
                eq(NotificationType.PURCHASE_STATUS),
                isNull(),
                eq("추천받은 상품, 구매하셨나요?"),
                eq("구매 여부를 알려주시면 추천이 더 정확해져요."),
                eq(NotificationResourceType.PURCHASE_CHECK),
                eq(saved.getId()),
                eq("PURCHASE_CHECK:" + saved.getId()),
                expiresAt.capture()
        );
        // DB에 저장된 createdAt은 마이크로초까지만 남으므로 오차를 둔다
        assertThat(expiresAt.getValue())
                .isCloseTo(saved.getCreatedAt().plusDays(7), within(1, ChronoUnit.MILLIS));
    }

    @Test
    void myGiftClick_createPurchaseCheck_usesMyGiftTitle() {
        ProductLinkClick myGiftClick = saveClick(ProductContext.MY_GIFT, null);

        purchaseCheckService.createPurchaseCheck(myGiftClick.getId());

        verify(notificationCreationService).createNotifications(
                any(), any(), isNull(), eq("내 선물 추천에서 본 상품, 구매하셨나요?"), anyString(),
                any(), anyLong(), anyString(), any(LocalDateTime.class));
    }

    @Test
    void friendGiftClick_createPurchaseCheck_usesFriendGiftTitleWithoutFriendName() {
        ProductLinkClick friendGiftClick = saveClick(ProductContext.FRIEND_GIFT, FRIEND_USER_ID);

        purchaseCheckService.createPurchaseCheck(friendGiftClick.getId());

        verify(notificationCreationService).createNotifications(
                any(), any(), isNull(), eq("친구 선물로 본 상품, 구매하셨나요?"), anyString(),
                any(), anyLong(), anyString(), any(LocalDateTime.class));
        verify(friendService, never()).findAllFriendNicknames(anyLong(), any());
    }

    @Test
    void clickWithPurchaseCheck_createPurchaseCheck_rejectsDuplicate() {
        savePurchaseCheck();

        assertThatThrownBy(() -> {
            purchaseCheckService.createPurchaseCheck(click.getId());
            entityManager.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void notificationFailure_createPurchaseCheck_propagatesException() {
        willThrow(new IllegalStateException("notification failed"))
                .given(notificationCreationService).createNotifications(
                        any(), any(), any(), anyString(), anyString(), any(), anyLong(), anyString(),
                        any(LocalDateTime.class));

        assertThatThrownBy(() -> purchaseCheckService.createPurchaseCheck(click.getId()))
                .isInstanceOf(IllegalStateException.class);
    }

    private PurchaseCheck savePurchaseCheck() {
        PurchaseCheck purchaseCheck = purchaseCheckRepository.save(
                PurchaseCheck.create(entityManager.getReference(ProductLinkClick.class, click.getId())));
        flushAndClear();
        return purchaseCheck;
    }

    private PurchaseCheck saveFriendGiftPurchaseCheck() {
        ProductLinkClick friendGiftClick = saveClick(ProductContext.FRIEND_GIFT, FRIEND_USER_ID);
        PurchaseCheck purchaseCheck = purchaseCheckRepository.save(
                PurchaseCheck.create(entityManager.getReference(ProductLinkClick.class, friendGiftClick.getId())));
        flushAndClear();
        return purchaseCheck;
    }

    private ProductLinkClick saveClick(ProductContext context, Long friendUserId) {
        ProductLinkClick newClick = new ProductLinkClick(
                USER_ID, entityManager.getReference(Product.class, product.getId()), context, friendUserId);
        entityManager.persist(newClick);
        flushAndClear();
        return newClick;
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
