package kr.ktb.zura.needu.product.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.CommonErrorCode;
import kr.ktb.zura.needu.friend.dto.response.FriendDetailResponse;
import kr.ktb.zura.needu.friend.exception.FriendErrorCode;
import kr.ktb.zura.needu.friend.service.FriendService;
import kr.ktb.zura.needu.product.dto.request.GiftProductSearchCondition;
import kr.ktb.zura.needu.product.dto.response.GiftProductResponse;
import kr.ktb.zura.needu.product.dto.response.ProductCursorPageResponse;
import kr.ktb.zura.needu.product.exception.ProductErrorCode;
import kr.ktb.zura.needu.product.repository.GiftProductRepository;
import kr.ktb.zura.needu.product.repository.GiftProductSummary;
import kr.ktb.zura.needu.product.repository.ProductPriceRange;
import kr.ktb.zura.needu.user.dto.response.UserSummaryResponse;
import kr.ktb.zura.needu.user.exception.UserErrorCode;
import kr.ktb.zura.needu.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
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
    @DisplayName("조회 결과가 요청 크기보다 많으면 다음 페이지가 존재하고 마지막 상품 기준 커서를 반환한다.")
    void moreItemsThanSize_findAllGiftProducts_returnsHasNextWithLastItemCursor() {
        //given
        givenFriend(true);
        given(giftProductRepository.findAllByUserIdAndPriceRange(FRIEND_USER_ID, MIN_PRICE, MAX_PRICE, Limit.of(3)))
                .willReturn(List.of(
                        createGiftProduct(30L, "0.900000", 49000),
                        createGiftProduct(20L, "0.800000", 39000),
                        createGiftProduct(10L, "0.700000", 30000)
                ));
        given(giftProductRepository.findPriceRangeByUserId(FRIEND_USER_ID)).willReturn(priceRange());

        //when
        ProductCursorPageResponse<GiftProductResponse> response =
                giftProductService.findAllGiftProducts(USER_ID, FRIEND_USER_ID, condition(null, 2));


        //then
        assertThat(response.items())
                .extracting(GiftProductResponse::recommendationId).containsExactly(30L, 20L);

        assertThat(response.hasNext()).isTrue();
        GiftProductCursor nextCursor = GiftProductCursor.decode(response.nextCursor());
        assertThat(nextCursor.score()).isEqualByComparingTo("0.800000");
        assertThat(nextCursor.id()).isEqualTo(20L);
    }

    @Test
    @DisplayName("친구 상품 조회는 score 기준 내림차순으로 조회된다")
    void findAllGiftProducts_Order_By_Score_Desc() {
        //given
        givenFriend(true);
        given(giftProductRepository.findAllByUserIdAndPriceRange(FRIEND_USER_ID, MIN_PRICE, MAX_PRICE, Limit.of(3)))
                .willReturn(List.of(
                        createGiftProduct(30L, "0.900000", 49000),
                        createGiftProduct(20L, "0.800000", 39000),
                        createGiftProduct(10L, "0.700000", 30000)
                ));
        given(giftProductRepository.findPriceRangeByUserId(FRIEND_USER_ID)).willReturn(priceRange());

        //when
        ProductCursorPageResponse<GiftProductResponse> response =
                giftProductService.findAllGiftProducts(USER_ID, FRIEND_USER_ID, condition(null, 2));

        //then
        assertThat(response.items()).extracting(GiftProductResponse::score)
                .containsExactly(new BigDecimal("0.900000"), new BigDecimal("0.800000"));
    }

    @Test
    @DisplayName("다음 페이지가 없으면 커서를 반환하지 않는다")
    void itemsNotExceedingSize_findAllGiftProducts_returnsLastPage() {
        //given
        givenFriend(true);
        given(giftProductRepository.findAllByUserIdAndPriceRange(FRIEND_USER_ID, MIN_PRICE, MAX_PRICE, Limit.of(3)))
                .willReturn(List.of(createGiftProduct(30L, "0.900000", 49000)));
        given(giftProductRepository.findPriceRangeByUserId(FRIEND_USER_ID)).willReturn(priceRange());

        //when
        ProductCursorPageResponse<GiftProductResponse> response =
                giftProductService.findAllGiftProducts(USER_ID, FRIEND_USER_ID, condition(null, 2));

        //then
        GiftProductResponse product = response.items().getFirst();
        assertThat(product.price()).isEqualTo(49000L);
        assertThat(product.matchingKeywords()).containsExactly("미니멀", "데일리");
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextCursor()).isNull();
    }

    @Test
    @DisplayName("다음 페이지가 있으면 커서를 반환한다")
    void cursorGiven_findAllGiftProducts_findsItemsAfterCursor() {
        //given
        givenFriend(true);
        String cursor = new GiftProductCursor(new BigDecimal("0.800000"), 20L).encode();
        given(giftProductRepository.findAllByUserIdAndPriceRangeAfterCursor(
                FRIEND_USER_ID, MIN_PRICE, MAX_PRICE, new BigDecimal("0.800000"), 20L, Limit.of(3)))
                .willReturn(List.of(createGiftProduct(10L, "0.700000", 30000)));
        given(giftProductRepository.findPriceRangeByUserId(FRIEND_USER_ID)).willReturn(priceRange());

        //when
        ProductCursorPageResponse<GiftProductResponse> response =
                giftProductService.findAllGiftProducts(USER_ID, FRIEND_USER_ID, condition(cursor, 2));

        //then
        assertThat(response.items())
                .extracting(GiftProductResponse::recommendationId).containsExactly(10L);
        assertThat(response.hasNext()).isFalse();
        verify(giftProductRepository, never()).findAllByUserIdAndPriceRange(anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("선물 상품이 없으면 빈 리스트를 반환한다.")
    void noGiftProducts_findAllGiftProducts_returnsEmptyItems() {
        //given
        givenFriend(true);
        given(giftProductRepository.findAllByUserIdAndPriceRange(FRIEND_USER_ID, MIN_PRICE, MAX_PRICE, Limit.of(21)))
                .willReturn(List.of());
        given(giftProductRepository.findPriceRangeByUserId(FRIEND_USER_ID))
                .willReturn(new ProductPriceRange(null, null));

        //when

        ProductCursorPageResponse<GiftProductResponse> response =
                giftProductService.findAllGiftProducts(USER_ID, FRIEND_USER_ID, condition(null, 20));

        //then
        assertThat(response.items()).isEmpty();
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextCursor()).isNull();
        assertThat(response.priceRange().minPrice()).isNull();
        assertThat(response.priceRange().maxPrice()).isNull();
    }

    @Test
    @DisplayName("최소 가격이 최대 가격보다 크면 COMMON_INVALID_INPUT 예외를 던진다.")
    void minPriceGreaterThanMaxPrice_findAllGiftProducts_throwsInvalidInput() {
        //given
        GiftProductSearchCondition condition = new GiftProductSearchCondition(50000L, 30000L, null, 20);

        //when & then
        assertThatThrownBy(() -> giftProductService.findAllGiftProducts(USER_ID, FRIEND_USER_ID, condition))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.COMMON_INVALID_INPUT);
        verifyNoInteractions(userService, friendService, giftProductRepository);
    }

    @Test
    @DisplayName("친구가 아닌 사용자의 상품 리스트를 조회하면 404 FRIEND_NOT_FOUND 예외를 던진다.")
    void notFriend_findAllGiftProducts_throwsFriendNotFoundWithoutFindingProducts() {
        //given
        given(friendService.findFriend(USER_ID, FRIEND_USER_ID))
                .willThrow(new BusinessException(FriendErrorCode.FRIEND_NOT_FOUND));

        //when & then
        assertThatThrownBy(() -> giftProductService.findAllGiftProducts(USER_ID, FRIEND_USER_ID, condition(null, 20)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(FriendErrorCode.FRIEND_NOT_FOUND);
        verifyNoInteractions(giftProductRepository);
    }

    @Test
    @DisplayName("친구가 취향 분석이 완료하지 않았을 경우 상품 리스트를 조회하면 403 PRODUCT_GIFT_RECOMMENDATION_FORBIDDEN 예외를 던진다.")
    void friendTasteAnalysisNotCompleted_findAllGiftProducts_throwsForbidden() {
        //given
        givenFriend(false);

        //when & then
        assertThatThrownBy(() -> giftProductService.findAllGiftProducts(USER_ID, FRIEND_USER_ID, condition(null, 20)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ProductErrorCode.PRODUCT_GIFT_RECOMMENDATION_FORBIDDEN);
        verifyNoInteractions(giftProductRepository); //상품조회 자체가 발생하면 안됨!
    }

    @Test
    @DisplayName("유효하지 않은 커서를 입력하면 400 COMMON_INVALID_REQUEST 예외를 던진다.")
    void invalidCursor_findAllGiftProducts_throwsInvalidRequest() {
        //given
        givenFriend(true);

        //when & then
        assertThatThrownBy(() ->
                giftProductService.findAllGiftProducts(USER_ID, FRIEND_USER_ID, condition("invalid!!", 20)))//유효하지 않은 cursor 값
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.COMMON_INVALID_REQUEST);
        verifyNoInteractions(giftProductRepository);//커서가 유효하지 않으면 조회 발생 x
    }

    @Test
    @DisplayName("로그인한 유저가 차단 상태일 경우 친구의 추천 상품 조회 시 403 USER_BLOCKED 예외를 던진다.")
    void blockedLoginUser_findAllGiftProducts_throwsUserBlocked() {

        //given
        willThrow(new BusinessException(UserErrorCode.USER_BLOCKED)).given(userService).validateActiveUser(USER_ID);

        //when & then
        assertThatThrownBy(() -> giftProductService.findAllGiftProducts(USER_ID, FRIEND_USER_ID, condition(null, 20)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.USER_BLOCKED);
        verifyNoInteractions(friendService, giftProductRepository);
    }

    private void givenFriend(boolean tasteAnalysisCompleted) {
        given(friendService.findFriend(USER_ID, FRIEND_USER_ID)).willReturn(
                new FriendDetailResponse(FRIEND_USER_ID, "친구", null, tasteAnalysisCompleted, null));
    }

    private GiftProductSearchCondition condition(String cursor, int size) {
        return new GiftProductSearchCondition(30000L, 50000L, cursor, size);
    }

    private ProductPriceRange priceRange() {
        return new ProductPriceRange(BigDecimal.valueOf(30000L), BigDecimal.valueOf(49000L));
    }

    private GiftProductSummary createGiftProduct(Long id, String score, long price) {
        return new GiftProductSummary(
                id, id + 1000, "상품" + id, null, null, "FASHION",
                BigDecimal.valueOf(price), new BigDecimal(score), null, List.of("미니멀", "데일리"));
    }
}
