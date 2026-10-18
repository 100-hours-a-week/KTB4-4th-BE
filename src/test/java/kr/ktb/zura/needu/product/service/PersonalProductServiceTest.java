package kr.ktb.zura.needu.product.service;

import java.math.BigDecimal;
import java.util.List;

import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.CommonErrorCode;
import kr.ktb.zura.needu.product.dto.request.PersonalProductSearchCondition;
import kr.ktb.zura.needu.product.dto.response.PersonalProductResponse;
import kr.ktb.zura.needu.product.dto.response.PriceRangeResponse;
import kr.ktb.zura.needu.product.dto.response.ProductCursorPageResponse;
import kr.ktb.zura.needu.product.repository.PersonalProductRepository;
import kr.ktb.zura.needu.product.repository.PersonalProductSummary;
import kr.ktb.zura.needu.product.type.ProductCategory;
import kr.ktb.zura.needu.product.type.ProductFeedbackType;
import kr.ktb.zura.needu.user.exception.UserErrorCode;
import kr.ktb.zura.needu.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;

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

    @Mock
    private ProductPriceRangeCacheService productPriceRangeCacheService;

    @InjectMocks
    private PersonalProductService personalProductService;

    @Test
    void moreItemsThanSize_findAllPersonalProducts_returnsHasNextWithLastItemCursor() {
        List<PersonalProductSummary> personalProducts = List.of(
                createPersonalProduct(30L, "0.900000", 5200),
                createPersonalProduct(20L, "0.800000", 3100),
                createPersonalProduct(10L, "0.700000", 1000)
        );
        given(personalProductRepository.findAllByUserIdAndPriceRange(
                USER_ID, MIN_PRICE, MAX_PRICE, null, Limit.of(3))).willReturn(personalProducts);
        given(productPriceRangeCacheService.findPersonalPriceRange(USER_ID)).willReturn(priceRange());

        ProductCursorPageResponse<PersonalProductResponse> response =
                personalProductService.findAllPersonalProducts(USER_ID, condition(null, 2));

        assertThat(response.items()).extracting(PersonalProductResponse::recommendationId).containsExactly(30L, 20L);
        assertThat(response.items()).extracting(PersonalProductResponse::score)
                .containsExactly(new BigDecimal("0.900000"), new BigDecimal("0.800000"));
        assertThat(response.hasNext()).isTrue();
        PersonalProductCursor nextCursor = PersonalProductCursor.decode(response.nextCursor());
        assertThat(nextCursor.score()).isEqualByComparingTo("0.800000");
        assertThat(nextCursor.id()).isEqualTo(20L);
    }

    @Test
    void itemsNotExceedingSize_findAllPersonalProducts_returnsLastPage() {
        given(personalProductRepository.findAllByUserIdAndPriceRange(USER_ID, MIN_PRICE, MAX_PRICE, null, Limit.of(3)))
                .willReturn(List.of(createPersonalProduct(30L, "0.900000", 5200)));
        given(productPriceRangeCacheService.findPersonalPriceRange(USER_ID)).willReturn(priceRange());

        ProductCursorPageResponse<PersonalProductResponse> response =
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
                USER_ID, MIN_PRICE, MAX_PRICE, null, new BigDecimal("0.800000"), 20L, Limit.of(3)))
                .willReturn(List.of(createPersonalProduct(10L, "0.700000", 1000)));
        given(productPriceRangeCacheService.findPersonalPriceRange(USER_ID)).willReturn(priceRange());

        ProductCursorPageResponse<PersonalProductResponse> response =
                personalProductService.findAllPersonalProducts(USER_ID, condition(cursor, 2));

        assertThat(response.items()).extracting(PersonalProductResponse::recommendationId).containsExactly(10L);
        assertThat(response.hasNext()).isFalse();
        verify(personalProductRepository, never()).findAllByUserIdAndPriceRange(anyLong(), any(), any(), any(), any());
    }

    @Test
    void noPersonalProducts_findAllPersonalProducts_returnsEmptyItems() {
        given(personalProductRepository.findAllByUserIdAndPriceRange(
                USER_ID, MIN_PRICE, MAX_PRICE, null, Limit.of(21))).willReturn(List.of());
        given(productPriceRangeCacheService.findPersonalPriceRange(USER_ID))
                .willReturn(new PriceRangeResponse(null, null));

        ProductCursorPageResponse<PersonalProductResponse> response =
                personalProductService.findAllPersonalProducts(USER_ID, condition(null, 20));

        assertThat(response.items()).isEmpty();
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextCursor()).isNull();
        assertThat(response.priceRange().minPrice()).isNull();
        assertThat(response.priceRange().maxPrice()).isNull();
    }

    @Test
    void minPriceGreaterThanMaxPrice_findAllPersonalProducts_throwsInvalidInput() {
        PersonalProductSearchCondition condition = new PersonalProductSearchCondition(50000L, 30000L, null, null, 20);

        assertThatThrownBy(() -> personalProductService.findAllPersonalProducts(USER_ID, condition))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.COMMON_INVALID_INPUT);
        verifyNoInteractions(userService, personalProductRepository, productPriceRangeCacheService);
    }

    @Test
    void invalidCursor_findAllPersonalProducts_throwsInvalidRequest() {

        assertThatThrownBy(() -> personalProductService.findAllPersonalProducts(USER_ID, condition("invalid!!", 20)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.COMMON_INVALID_REQUEST);
        verifyNoInteractions(personalProductRepository);
        verifyNoInteractions(productPriceRangeCacheService);
    }

    @Test
    void blockedUser_findAllPersonalProducts_throwsUserBlocked() {
        willThrow(new BusinessException(UserErrorCode.USER_BLOCKED)).given(userService).validateActiveUser(USER_ID);

        assertThatThrownBy(() -> personalProductService.findAllPersonalProducts(USER_ID, condition(null, 20)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.USER_BLOCKED);
        verifyNoInteractions(personalProductRepository, productPriceRangeCacheService);
    }

    @Test
    void categoryGiven_findAllPersonalProducts_findsOnlyThatCategory() {
        PersonalProductSearchCondition condition = new PersonalProductSearchCondition(
                MIN_PRICE.longValue(), MAX_PRICE.longValue(), "LIVING", null, 2);
        given(personalProductRepository.findAllByUserIdAndPriceRange(
                USER_ID, MIN_PRICE, MAX_PRICE, ProductCategory.LIVING, Limit.of(3)))
                .willReturn(List.of(createPersonalProduct(30L, "0.900000", 5200)));
        given(productPriceRangeCacheService.findPersonalPriceRange(USER_ID)).willReturn(priceRange());

        ProductCursorPageResponse<PersonalProductResponse> response =
                personalProductService.findAllPersonalProducts(USER_ID, condition);

        assertThat(response.items()).extracting(PersonalProductResponse::recommendationId).containsExactly(30L);
        assertThat(response.priceRange()).isEqualTo(priceRange());
    }

    @Test
    void categoryAndCursorGiven_findAllPersonalProducts_findsThatCategoryAfterCursor() {
        String cursor = new PersonalProductCursor(new BigDecimal("0.800000"), 20L).encode();
        PersonalProductSearchCondition condition = new PersonalProductSearchCondition(
                MIN_PRICE.longValue(), MAX_PRICE.longValue(), "BEAUTY", cursor, 2);
        given(personalProductRepository.findAllByUserIdAndPriceRangeAfterCursor(
                USER_ID, MIN_PRICE, MAX_PRICE, ProductCategory.BEAUTY, new BigDecimal("0.800000"), 20L, Limit.of(3)))
                .willReturn(List.of(createPersonalProduct(10L, "0.700000", 1000)));
        given(productPriceRangeCacheService.findPersonalPriceRange(USER_ID)).willReturn(priceRange());

        ProductCursorPageResponse<PersonalProductResponse> response =
                personalProductService.findAllPersonalProducts(USER_ID, condition);

        assertThat(response.items()).extracting(PersonalProductResponse::recommendationId).containsExactly(10L);
    }

    @Test
    void unknownCategory_findAllPersonalProducts_throwsInvalidInput() {
        PersonalProductSearchCondition condition = new PersonalProductSearchCondition(
                MIN_PRICE.longValue(), MAX_PRICE.longValue(), "living", null, 20);

        assertThatThrownBy(() -> personalProductService.findAllPersonalProducts(USER_ID, condition))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.COMMON_INVALID_INPUT);
        verifyNoInteractions(userService, personalProductRepository, productPriceRangeCacheService);
    }

    @Test
    void feedbackSaved_findAllPersonalProducts_returnsMyFeedback() {
        PersonalProductSummary liked = new PersonalProductSummary(
                30L, 1030L, "상품30", null, null, ProductCategory.LIVING,
                BigDecimal.valueOf(5200), new BigDecimal("0.900000"), null, ProductFeedbackType.LIKE);
        given(personalProductRepository.findAllByUserIdAndPriceRange(
                USER_ID, MIN_PRICE, MAX_PRICE, null, Limit.of(3)))
                .willReturn(List.of(liked, createPersonalProduct(20L, "0.800000", 3100)));
        given(productPriceRangeCacheService.findPersonalPriceRange(USER_ID)).willReturn(priceRange());

        ProductCursorPageResponse<PersonalProductResponse> response =
                personalProductService.findAllPersonalProducts(USER_ID, condition(null, 2));

        assertThat(response.items()).extracting(PersonalProductResponse::myFeedback)
                .containsExactly(ProductFeedbackType.LIKE, null);
    }

    private PersonalProductSearchCondition condition(String cursor, int size) {
        return new PersonalProductSearchCondition(MIN_PRICE.longValue(), MAX_PRICE.longValue(), null, cursor, size);
    }

    private PriceRangeResponse priceRange() {
        return new PriceRangeResponse(1000L, 5200L);
    }

    private PersonalProductSummary createPersonalProduct(Long id, String score, long price) {
        return new PersonalProductSummary(
                id, id + 1000, "상품" + id, null, null, null,
                BigDecimal.valueOf(price), new BigDecimal(score), null, null);
    }
}
