package kr.ktb.zura.needu.product.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import jakarta.persistence.EntityManager;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.CommonErrorCode;
import kr.ktb.zura.needu.product.dto.request.CreateProductLinkClickRequest;
import kr.ktb.zura.needu.product.entity.Product;
import kr.ktb.zura.needu.product.entity.ProductLinkClick;
import kr.ktb.zura.needu.product.repository.ProductLinkClickRepository;
import kr.ktb.zura.needu.product.type.PlatformType;
import kr.ktb.zura.needu.product.type.ProductContext;
import kr.ktb.zura.needu.user.exception.UserErrorCode;
import kr.ktb.zura.needu.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.BDDMockito.willThrow;

// 같은 클릭을 기간 안에 다시 기록하지 않는 규칙은 실제 저장 결과로 확인해야 해서 실제 JPA(H2) 위에서 검증한다.
@DataJpaTest
@Import(ProductLinkClickService.class)
class ProductLinkClickServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long FRIEND_USER_ID = 321L;

    @Autowired
    private ProductLinkClickService productLinkClickService;

    @Autowired
    private ProductLinkClickRepository productLinkClickRepository;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private UserService userService;

    private Product product;

    @BeforeEach
    void setUp() {
        product = new Product(
                PlatformType.COUPANG, "external-1", "섬유유연제", null, null,
                new BigDecimal("16500.00"), null, null, null);
        entityManager.persist(product);
        flushAndClear();
    }

    @Test
    void firstClick_createLinkClick_savesClick() {
        productLinkClickService.createLinkClick(USER_ID, request(ProductContext.PERSONAL, null));
        flushAndClear();

        assertThat(productLinkClickRepository.findAll()).singleElement().satisfies(click -> {
            assertThat(click.getUserId()).isEqualTo(USER_ID);
            assertThat(click.getProduct().getId()).isEqualTo(product.getId());
            assertThat(click.getContext()).isEqualTo(ProductContext.PERSONAL);
            assertThat(click.getFriendUserId()).isNull();
        });
    }

    @Test
    void friendGiftClick_createLinkClick_savesFriendUserId() {
        productLinkClickService.createLinkClick(USER_ID, request(ProductContext.FRIEND_GIFT, FRIEND_USER_ID));
        flushAndClear();

        assertThat(productLinkClickRepository.findAll()).singleElement()
                .extracting(ProductLinkClick::getFriendUserId).isEqualTo(FRIEND_USER_ID);
    }

    @Test
    void friendUserIdWithMyContext_createLinkClick_ignoresFriendUserId() {
        productLinkClickService.createLinkClick(USER_ID, request(ProductContext.MY_GIFT, FRIEND_USER_ID));
        flushAndClear();

        assertThat(productLinkClickRepository.findAll()).singleElement()
                .extracting(ProductLinkClick::getFriendUserId).isNull();
    }

    @Test
    void sameClickWithinWindow_createLinkClick_keepsSingleClick() {
        productLinkClickService.createLinkClick(USER_ID, request(ProductContext.PERSONAL, null));
        flushAndClear();
        Long firstClickId = productLinkClickRepository.findAll().getFirst().getId();
        moveCreatedAt(firstClickId, LocalDateTime.now().minusDays(6));

        productLinkClickService.createLinkClick(USER_ID, request(ProductContext.PERSONAL, null));
        flushAndClear();

        assertThat(productLinkClickRepository.findAll()).extracting(ProductLinkClick::getId)
                .containsExactly(firstClickId);
    }

    @Test
    void sameClickAfterWindow_createLinkClick_savesNewClick() {
        productLinkClickService.createLinkClick(USER_ID, request(ProductContext.PERSONAL, null));
        flushAndClear();
        moveCreatedAt(productLinkClickRepository.findAll().getFirst().getId(), LocalDateTime.now().minusDays(8));

        productLinkClickService.createLinkClick(USER_ID, request(ProductContext.PERSONAL, null));
        flushAndClear();

        assertThat(productLinkClickRepository.findAll()).hasSize(2);
    }

    @Test
    void differentContextOrUser_createLinkClick_savesEachClick() {
        productLinkClickService.createLinkClick(USER_ID, request(ProductContext.PERSONAL, null));
        productLinkClickService.createLinkClick(USER_ID, request(ProductContext.MY_GIFT, null));
        productLinkClickService.createLinkClick(OTHER_USER_ID, request(ProductContext.PERSONAL, null));
        flushAndClear();

        assertThat(productLinkClickRepository.findAll())
                .extracting(ProductLinkClick::getUserId, ProductLinkClick::getContext)
                .containsExactlyInAnyOrder(
                        tuple(USER_ID, ProductContext.PERSONAL),
                        tuple(USER_ID, ProductContext.MY_GIFT),
                        tuple(OTHER_USER_ID, ProductContext.PERSONAL));
    }

    @Test
    void unknownProduct_createLinkClick_throwsInvalidInputWithoutSaving() {
        CreateProductLinkClickRequest request =
                new CreateProductLinkClickRequest(product.getId() + 999, "PERSONAL", null, null);

        assertThatThrownBy(() -> productLinkClickService.createLinkClick(USER_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.COMMON_INVALID_INPUT);
        assertThat(productLinkClickRepository.findAll()).isEmpty();
    }

    @Test
    void inactiveUser_createLinkClick_throwsUserErrorWithoutSaving() {
        willThrow(new BusinessException(UserErrorCode.USER_BLOCKED)).given(userService).validateActiveUser(USER_ID);

        assertThatThrownBy(() -> productLinkClickService.createLinkClick(
                USER_ID, request(ProductContext.PERSONAL, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.USER_BLOCKED);
        assertThat(productLinkClickRepository.findAll()).isEmpty();
    }

    private CreateProductLinkClickRequest request(ProductContext context, Long friendUserId) {
        return new CreateProductLinkClickRequest(product.getId(), context.name(), friendUserId, null);
    }

    // createdAt은 생성 시각으로 고정되어 수정할 수 없으므로, 기간 경계를 만들 때만 직접 옮긴다
    private void moveCreatedAt(Long clickId, LocalDateTime createdAt) {
        entityManager.createQuery("update ProductLinkClick plc set plc.createdAt = :createdAt where plc.id = :id")
                .setParameter("createdAt", createdAt)
                .setParameter("id", clickId)
                .executeUpdate();
        entityManager.clear();
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
