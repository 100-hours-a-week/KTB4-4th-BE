package kr.ktb.zura.needu.product.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.CommonErrorCode;
import kr.ktb.zura.needu.common.response.CursorPageResponse;
import kr.ktb.zura.needu.friend.dto.response.FriendDetailResponse;
import kr.ktb.zura.needu.friend.exception.FriendErrorCode;
import kr.ktb.zura.needu.friend.service.FriendService;
import kr.ktb.zura.needu.product.dto.request.GiftProductSearchCondition;
import kr.ktb.zura.needu.product.dto.response.GiftProductResponse;
import kr.ktb.zura.needu.product.entity.GiftProduct;
import kr.ktb.zura.needu.product.entity.Product;
import kr.ktb.zura.needu.product.exception.ProductErrorCode;
import kr.ktb.zura.needu.product.repository.GiftProductRepository;
import kr.ktb.zura.needu.product.type.PlatformType;
import kr.ktb.zura.needu.user.dto.response.UserSummaryResponse;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class GiftProductServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long FRIEND_USER_ID = 123L;
    private static final BigDecimal MIN_PRICE = BigDecimal.valueOf(30000L);
    private static final BigDecimal MAX_PRICE = BigDecimal.valueOf(50000L);

    @Mock
    private UserService userService;

    @Mock
    private FriendService friendService;

    @Mock
    private GiftProductRepository giftProductRepository;

    @InjectMocks
    private GiftProductService giftProductService;

    @Test
    void moreItemsThanSize_findAllGiftProducts_returnsHasNextWithLastItemCursor() {
        givenFriend(true);
        given(giftProductRepository.findAllByUserIdAndPriceRange(FRIEND_USER_ID, MIN_PRICE, MAX_PRICE, Limit.of(3)))
                .willReturn(List.of(
                        createGiftProduct(30L, "0.900000", 49000),
                        createGiftProduct(20L, "0.800000", 39000),
                        createGiftProduct(10L, "0.700000", 30000)
                ));

        CursorPageResponse<GiftProductResponse> response =
                giftProductService.findAllGiftProducts(USER_ID, FRIEND_USER_ID, condition(null, 2));

        assertThat(response.items())
                .extracting(GiftProductResponse::recommendationId).containsExactly(30L, 20L);
        assertThat(response.hasNext()).isTrue();
        GiftProductCursor nextCursor = GiftProductCursor.decode(response.nextCursor());
        assertThat(nextCursor.score()).isEqualByComparingTo("0.800000");
        assertThat(nextCursor.id()).isEqualTo(20L);
    }

    @Test
    void itemsNotExceedingSize_findAllGiftProducts_returnsLastPage() {
        givenFriend(true);
        given(giftProductRepository.findAllByUserIdAndPriceRange(FRIEND_USER_ID, MIN_PRICE, MAX_PRICE, Limit.of(3)))
                .willReturn(List.of(createGiftProduct(30L, "0.900000", 49000)));

        CursorPageResponse<GiftProductResponse> response =
                giftProductService.findAllGiftProducts(USER_ID, FRIEND_USER_ID, condition(null, 2));

        GiftProductResponse product = response.items().getFirst();
        assertThat(product.price()).isEqualTo(49000L);
        assertThat(product.matchingKeywords()).containsExactly("미니멀", "데일리");
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextCursor()).isNull();
    }

    @Test
    void cursorGiven_findAllGiftProducts_findsItemsAfterCursor() {
        givenFriend(true);
        String cursor = new GiftProductCursor(new BigDecimal("0.800000"), 20L).encode();
        given(giftProductRepository.findAllByUserIdAndPriceRangeAfterCursor(
                FRIEND_USER_ID, MIN_PRICE, MAX_PRICE, new BigDecimal("0.800000"), 20L, Limit.of(3)))
                .willReturn(List.of(createGiftProduct(10L, "0.700000", 30000)));

        CursorPageResponse<GiftProductResponse> response =
                giftProductService.findAllGiftProducts(USER_ID, FRIEND_USER_ID, condition(cursor, 2));

        assertThat(response.items())
                .extracting(GiftProductResponse::recommendationId).containsExactly(10L);
        assertThat(response.hasNext()).isFalse();
        verify(giftProductRepository, never()).findAllByUserIdAndPriceRange(anyLong(), any(), any(), any());
    }

    @Test
    void noGiftProducts_findAllGiftProducts_returnsEmptyItems() {
        givenFriend(true);
        given(giftProductRepository.findAllByUserIdAndPriceRange(FRIEND_USER_ID, MIN_PRICE, MAX_PRICE, Limit.of(21)))
                .willReturn(List.of());

        CursorPageResponse<GiftProductResponse> response =
                giftProductService.findAllGiftProducts(USER_ID, FRIEND_USER_ID, condition(null, 20));

        assertThat(response.items()).isEmpty();
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextCursor()).isNull();
    }

    @Test
    void minPriceGreaterThanMaxPrice_findAllGiftProducts_throwsInvalidInput() {
        GiftProductSearchCondition condition = new GiftProductSearchCondition(50000L, 30000L, null, 20);

        assertThatThrownBy(() -> giftProductService.findAllGiftProducts(USER_ID, FRIEND_USER_ID, condition))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.COMMON_INVALID_INPUT);
        verifyNoInteractions(userService, friendService, giftProductRepository);
    }

    @Test
    void notFriend_findAllGiftProducts_throwsFriendNotFoundWithoutFindingProducts() {
        given(userService.findUserSummary(USER_ID)).willReturn(createUserSummary());
        given(friendService.findFriend(USER_ID, FRIEND_USER_ID))
                .willThrow(new BusinessException(FriendErrorCode.FRIEND_NOT_FOUND));

        assertThatThrownBy(() -> giftProductService.findAllGiftProducts(USER_ID, FRIEND_USER_ID, condition(null, 20)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(FriendErrorCode.FRIEND_NOT_FOUND);
        verifyNoInteractions(giftProductRepository);
    }

    @Test
    void friendTasteAnalysisNotCompleted_findAllGiftProducts_throwsForbidden() {
        givenFriend(false);

        assertThatThrownBy(() -> giftProductService.findAllGiftProducts(USER_ID, FRIEND_USER_ID, condition(null, 20)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ProductErrorCode.PRODUCT_GIFT_RECOMMENDATION_FORBIDDEN);
        verifyNoInteractions(giftProductRepository);
    }

    @Test
    void invalidCursor_findAllGiftProducts_throwsInvalidRequest() {
        givenFriend(true);

        assertThatThrownBy(() ->
                giftProductService.findAllGiftProducts(USER_ID, FRIEND_USER_ID, condition("invalid!!", 20)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.COMMON_INVALID_REQUEST);
        verifyNoInteractions(giftProductRepository);
    }

    @Test
    void blockedLoginUser_findAllGiftProducts_throwsUserBlocked() {
        given(userService.findUserSummary(USER_ID)).willThrow(new BusinessException(UserErrorCode.USER_BLOCKED));

        assertThatThrownBy(() -> giftProductService.findAllGiftProducts(USER_ID, FRIEND_USER_ID, condition(null, 20)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.USER_BLOCKED);
        verifyNoInteractions(friendService, giftProductRepository);
    }

    private void givenFriend(boolean tasteAnalysisCompleted) {
        given(userService.findUserSummary(USER_ID)).willReturn(createUserSummary());
        given(friendService.findFriend(USER_ID, FRIEND_USER_ID)).willReturn(
                new FriendDetailResponse(FRIEND_USER_ID, "친구", null, tasteAnalysisCompleted, null));
    }

    private GiftProductSearchCondition condition(String cursor, int size) {
        return new GiftProductSearchCondition(30000L, 50000L, cursor, size);
    }

    private UserSummaryResponse createUserSummary() {
        return new UserSummaryResponse(USER_ID, "니듀", LocalDate.of(2000, 1, 1), true);
    }

    private GiftProduct createGiftProduct(Long id, String score, long price) {
        Product product = new Product(
                PlatformType.COUPANG, String.valueOf(id), "상품" + id, "FASHION", null,
                BigDecimal.valueOf(price), null, null, null);
        GiftProduct giftProduct = new GiftProduct(
                FRIEND_USER_ID, product, new BigDecimal(score), null, List.of("미니멀", "데일리"));
        // ID는 DB에서 생성되므로 단위 테스트에서만 직접 설정한다.
        ReflectionTestUtils.setField(product, "id", id + 1000);
        ReflectionTestUtils.setField(giftProduct, "id", id);
        return giftProduct;
    }
}
