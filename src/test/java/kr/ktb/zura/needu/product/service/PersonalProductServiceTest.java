package kr.ktb.zura.needu.product.service;

import java.math.BigDecimal;
import java.util.List;

import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.CommonErrorCode;
import kr.ktb.zura.needu.common.response.CursorPageResponse;
import kr.ktb.zura.needu.product.dto.request.PersonalProductSearchCondition;
import kr.ktb.zura.needu.product.dto.response.PersonalProductResponse;
import kr.ktb.zura.needu.product.entity.PersonalProduct;
import kr.ktb.zura.needu.product.entity.Product;
import kr.ktb.zura.needu.product.repository.PersonalProductRepository;
import kr.ktb.zura.needu.product.type.PlatformType;
import kr.ktb.zura.needu.user.exception.UserErrorCode;
import kr.ktb.zura.needu.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class PersonalProductServiceTest {

    private static final Long USER_ID = 1L;
    private static final BigDecimal MIN_PRICE = BigDecimal.valueOf(1000L);
    private static final BigDecimal MAX_PRICE = BigDecimal.valueOf(10000L);

    @Mock
    private UserService userService;

    @Mock
    private PersonalProductRepository personalProductRepository;

    @InjectMocks
    private PersonalProductService personalProductService;

    @Test
    void moreItemsThanSize_findAllPersonalProducts_returnsHasNextWithLastItemCursor() {
        List<PersonalProduct> personalProducts = List.of(
                createPersonalProduct(30L, "0.900000", 5200),
                createPersonalProduct(20L, "0.800000", 3100),
                createPersonalProduct(10L, "0.700000", 1000)
        );
        given(personalProductRepository.findAllByUserIdAndPriceRange(
                USER_ID, MIN_PRICE, MAX_PRICE, Limit.of(3))).willReturn(personalProducts);

        CursorPageResponse<PersonalProductResponse> response =
                personalProductService.findAllPersonalProducts(USER_ID, condition(null, 2));

        assertThat(response.items()).extracting(PersonalProductResponse::recommendationId).containsExactly(30L, 20L);
        assertThat(response.hasNext()).isTrue();
        PersonalProductCursor nextCursor = PersonalProductCursor.decode(response.nextCursor());
        assertThat(nextCursor.score()).isEqualByComparingTo("0.800000");
        assertThat(nextCursor.id()).isEqualTo(20L);
    }

    @Test
    void itemsNotExceedingSize_findAllPersonalProducts_returnsLastPage() {
        given(personalProductRepository.findAllByUserIdAndPriceRange(USER_ID, MIN_PRICE, MAX_PRICE, Limit.of(3)))
                .willReturn(List.of(createPersonalProduct(30L, "0.900000", 5200)));

        CursorPageResponse<PersonalProductResponse> response =
                personalProductService.findAllPersonalProducts(USER_ID, condition(null, 2));

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().price()).isEqualTo(5200L);
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextCursor()).isNull();
    }

    @Test
    void cursorGiven_findAllPersonalProducts_findsItemsAfterCursor() {
        String cursor = new PersonalProductCursor(new BigDecimal("0.800000"), 20L).encode();
        given(personalProductRepository.findAllByUserIdAndPriceRangeAfterCursor(
                USER_ID, MIN_PRICE, MAX_PRICE, new BigDecimal("0.800000"), 20L, Limit.of(3)))
                .willReturn(List.of(createPersonalProduct(10L, "0.700000", 1000)));

        CursorPageResponse<PersonalProductResponse> response =
                personalProductService.findAllPersonalProducts(USER_ID, condition(cursor, 2));

        assertThat(response.items()).extracting(PersonalProductResponse::recommendationId).containsExactly(10L);
        assertThat(response.hasNext()).isFalse();
        verify(personalProductRepository, never()).findAllByUserIdAndPriceRange(anyLong(), any(), any(), any());
    }

    @Test
    void noPersonalProducts_findAllPersonalProducts_returnsEmptyItems() {
        given(personalProductRepository.findAllByUserIdAndPriceRange(
                USER_ID, MIN_PRICE, MAX_PRICE, Limit.of(21))).willReturn(List.of());

        CursorPageResponse<PersonalProductResponse> response =
                personalProductService.findAllPersonalProducts(USER_ID, condition(null, 20));

        assertThat(response.items()).isEmpty();
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextCursor()).isNull();
    }

    @Test
    void minPriceGreaterThanMaxPrice_findAllPersonalProducts_throwsInvalidInput() {
        PersonalProductSearchCondition condition = new PersonalProductSearchCondition(50000L, 30000L, null, 20);

        assertThatThrownBy(() -> personalProductService.findAllPersonalProducts(USER_ID, condition))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.COMMON_INVALID_INPUT);
        verifyNoInteractions(userService, personalProductRepository);
    }

    @Test
    void invalidCursor_findAllPersonalProducts_throwsInvalidRequest() {

        assertThatThrownBy(() -> personalProductService.findAllPersonalProducts(USER_ID, condition("invalid!!", 20)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.COMMON_INVALID_REQUEST);
        verifyNoInteractions(personalProductRepository);
    }

    @Test
    void blockedUser_findAllPersonalProducts_throwsUserBlocked() {
        willThrow(new BusinessException(UserErrorCode.USER_BLOCKED)).given(userService).validateActiveUser(USER_ID);

        assertThatThrownBy(() -> personalProductService.findAllPersonalProducts(USER_ID, condition(null, 20)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.USER_BLOCKED);
        verifyNoInteractions(personalProductRepository);
    }

    private PersonalProductSearchCondition condition(String cursor, int size) {
        return new PersonalProductSearchCondition(MIN_PRICE.longValue(), MAX_PRICE.longValue(), cursor, size);
    }

    private PersonalProduct createPersonalProduct(Long id, String score, long price) {
        Product product = new Product(
                PlatformType.COUPANG, String.valueOf(id), "상품" + id, null, null,
                BigDecimal.valueOf(price), null, null, null);
        PersonalProduct personalProduct = new PersonalProduct(USER_ID, product, new BigDecimal(score), null);
        // ID는 DB에서 생성되므로 단위 테스트에서만 직접 설정한다.
        ReflectionTestUtils.setField(product, "id", id + 1000);
        ReflectionTestUtils.setField(personalProduct, "id", id);
        return personalProduct;
    }
}
