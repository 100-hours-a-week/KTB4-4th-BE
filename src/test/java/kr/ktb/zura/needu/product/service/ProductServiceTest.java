package kr.ktb.zura.needu.product.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.CommonErrorCode;
import kr.ktb.zura.needu.friend.exception.FriendErrorCode;
import kr.ktb.zura.needu.product.dto.response.ProductCategoryResponse;
import kr.ktb.zura.needu.product.dto.response.ProductDetailResponse;
import kr.ktb.zura.needu.product.dto.response.ProductRecommendationResponse;
import kr.ktb.zura.needu.product.dto.response.ProductSummaryResponse;
import kr.ktb.zura.needu.product.entity.GiftProduct;
import kr.ktb.zura.needu.product.entity.PersonalProduct;
import kr.ktb.zura.needu.product.entity.Product;
import kr.ktb.zura.needu.product.entity.ProductFeedback;
import kr.ktb.zura.needu.product.exception.ProductErrorCode;
import kr.ktb.zura.needu.product.repository.GiftProductRepository;
import kr.ktb.zura.needu.product.repository.PersonalProductRepository;
import kr.ktb.zura.needu.product.repository.ProductFeedbackRepository;
import kr.ktb.zura.needu.product.repository.ProductRepository;
import kr.ktb.zura.needu.product.type.PlatformType;
import kr.ktb.zura.needu.product.type.ProductCategory;
import kr.ktb.zura.needu.product.type.ProductContext;
import kr.ktb.zura.needu.product.type.ProductFeedbackType;
import kr.ktb.zura.needu.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long FRIEND_USER_ID = 123L;
    private static final Long PRODUCT_ID = 2992L;

    @Mock
    private UserService userService;

    @Mock
    private GiftProductService giftProductService;

    @Mock
    private PersonalProductRepository personalProductRepository;

    @Mock
    private GiftProductRepository giftProductRepository;

    @Mock
    private ProductFeedbackRepository productFeedbackRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    @Test
    void existingProduct_findProductSummary_returnsSummaryWithLongPrice() {
        given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product()));

        ProductSummaryResponse response = productService.findProductSummary(PRODUCT_ID);

        assertThat(response.productId()).isEqualTo(PRODUCT_ID);
        assertThat(response.name()).isEqualTo("섬유유연제");
        assertThat(response.price()).isEqualTo(16500L);
        assertThat(response.category()).isEqualTo(ProductCategory.LIVING);
        assertThat(response.imageUrl()).isEqualTo("https://image.test/product.png");
    }

    @Test
    void missingProduct_findProductSummary_throwsNotFound() {
        given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> productService.findProductSummary(PRODUCT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ProductErrorCode.PRODUCT_NOT_FOUND);
    }

    @Test
    void productIds_findAllProductSummaries_returnsSummariesById() {
        given(productRepository.findAllById(List.of(PRODUCT_ID))).willReturn(List.of(product()));

        Map<Long, ProductSummaryResponse> response = productService.findAllProductSummaries(List.of(PRODUCT_ID));

        assertThat(response).containsOnlyKeys(PRODUCT_ID);
        assertThat(response.get(PRODUCT_ID).name()).isEqualTo("섬유유연제");
    }

    @Test
    void missingProduct_findAllProductSummaries_throwsNotFound() {
        given(productRepository.findAllById(List.of(PRODUCT_ID))).willReturn(List.of());

        assertThatThrownBy(() -> productService.findAllProductSummaries(List.of(PRODUCT_ID)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ProductErrorCode.PRODUCT_NOT_FOUND);
    }

    @Test
    void categoriesDefined_findAllCategories_returnsAllInTabOrder() {
        assertThat(productService.findAllCategories())
                .hasSize(ProductCategory.values().length)
                .extracting(ProductCategoryResponse::code, ProductCategoryResponse::name)
                .startsWith(
                        tuple("VOUCHER", "교환권"),
                        tuple("LIVING", "리빙"));
    }

    @Test
    void personalRecommendation_findProduct_returnsProductWithReasonAndMyFeedback() {
        PersonalProduct personalProduct = personalProduct(55L, product());
        given(personalProductRepository.findAllActiveByUserIdAndProductId(USER_ID, PRODUCT_ID))
                .willReturn(List.of(personalProduct));
        givenFeedback(ProductContext.PERSONAL, ProductFeedbackType.LIKE);

        ProductDetailResponse response = productService.findProduct(USER_ID, PRODUCT_ID, ProductContext.PERSONAL, null);

        assertThat(response.productId()).isEqualTo(PRODUCT_ID);
        assertThat(response.name()).isEqualTo("섬유유연제");
        assertThat(response.category()).isEqualTo(ProductCategory.LIVING);
        assertThat(response.description()).isEqualTo("상품 설명");
        assertThat(response.price()).isEqualTo(16500L);
        assertThat(response.productImageUrl()).isEqualTo("https://image.test/product.png");
        assertThat(response.purchaseUrl()).isEqualTo("https://shop.test/product");
        assertThat(response.recommendation())
                .isEqualTo(new ProductRecommendationResponse(55L, "향기 취향과 잘 맞아요.", List.of()));
        assertThat(response.myFeedback()).isEqualTo(ProductFeedbackType.LIKE);
    }

    @Test
    void myGiftRecommendation_findProduct_returnsMatchingKeywordsAndMyGiftFeedback() {
        given(giftProductRepository.findAllActiveByUserIdAndProductId(USER_ID, PRODUCT_ID))
                .willReturn(List.of(giftProduct(USER_ID, 77L, product())));
        given(productFeedbackRepository.findByUserIdAndProductIdAndContext(USER_ID, PRODUCT_ID, ProductContext.MY_GIFT))
                .willReturn(Optional.empty());

        ProductDetailResponse response = productService.findProduct(USER_ID, PRODUCT_ID, ProductContext.MY_GIFT, null);

        assertThat(response.recommendation())
                .isEqualTo(new ProductRecommendationResponse(77L, "선물로 좋아요.", List.of("향기", "홈케어")));
        assertThat(response.myFeedback()).isNull();
    }

    @Test
    void notInRecommendations_findProduct_throwsRecommendationNotFound() {
        given(personalProductRepository.findAllActiveByUserIdAndProductId(USER_ID, PRODUCT_ID)).willReturn(List.of());

        assertThatThrownBy(() -> productService.findProduct(USER_ID, PRODUCT_ID, ProductContext.PERSONAL, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ProductErrorCode.PRODUCT_RECOMMENDATION_NOT_FOUND);
    }

    @Test
    void friendGiftRecommendation_findProduct_returnsFriendsRecommendationWithoutMyFeedback() {
        given(giftProductRepository.findAllActiveByUserIdAndProductId(FRIEND_USER_ID, PRODUCT_ID))
                .willReturn(List.of(giftProduct(FRIEND_USER_ID, 88L, product())));

        ProductDetailResponse response =
                productService.findProduct(USER_ID, PRODUCT_ID, ProductContext.FRIEND_GIFT, FRIEND_USER_ID);

        assertThat(response.recommendation().recommendationId()).isEqualTo(88L);
        assertThat(response.recommendation().matchingKeywords()).containsExactly("향기", "홈케어");
        assertThat(response.myFeedback()).isNull();
        verifyNoInteractions(productFeedbackRepository, personalProductRepository);
    }

    @Test
    void notFriend_findProduct_throwsFriendNotFoundWithoutFindingProduct() {
        willThrow(new BusinessException(FriendErrorCode.FRIEND_NOT_FOUND))
                .given(giftProductService).validateFriendGiftViewable(USER_ID, FRIEND_USER_ID);

        assertThatThrownBy(() -> productService.findProduct(
                USER_ID, PRODUCT_ID, ProductContext.FRIEND_GIFT, FRIEND_USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(FriendErrorCode.FRIEND_NOT_FOUND);
        verifyNoInteractions(giftProductRepository);
    }

    @Test
    void friendTasteAnalysisNotCompleted_findProduct_throwsForbidden() {
        willThrow(new BusinessException(ProductErrorCode.PRODUCT_GIFT_RECOMMENDATION_FORBIDDEN))
                .given(giftProductService).validateFriendGiftViewable(USER_ID, FRIEND_USER_ID);

        assertThatThrownBy(() -> productService.findProduct(
                USER_ID, PRODUCT_ID, ProductContext.FRIEND_GIFT, FRIEND_USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ProductErrorCode.PRODUCT_GIFT_RECOMMENDATION_FORBIDDEN);
        verifyNoInteractions(giftProductRepository);
    }

    @Test
    void friendGiftWithoutUserId_findProduct_throwsInvalidInputWithoutUserCheck() {
        assertThatThrownBy(() -> productService.findProduct(USER_ID, PRODUCT_ID, ProductContext.FRIEND_GIFT, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.COMMON_INVALID_INPUT);
        verifyNoInteractions(userService, giftProductService, giftProductRepository);
    }

    private void givenFeedback(ProductContext context, ProductFeedbackType feedbackType) {
        given(productFeedbackRepository.findByUserIdAndProductIdAndContext(USER_ID, PRODUCT_ID, context))
                .willReturn(Optional.of(new ProductFeedback(USER_ID, product(), context, feedbackType)));
    }

    private Product product() {
        Product product = new Product(
                PlatformType.COUPANG, "external-1", "섬유유연제", ProductCategory.LIVING, "상품 설명",
                new BigDecimal("16500.00"), "https://image.test/product.png", null, "https://shop.test/product");
        ReflectionTestUtils.setField(product, "id", PRODUCT_ID);
        return product;
    }

    private PersonalProduct personalProduct(Long id, Product product) {
        PersonalProduct personalProduct =
                new PersonalProduct(USER_ID, product, new BigDecimal("0.900000"), "향기 취향과 잘 맞아요.");
        ReflectionTestUtils.setField(personalProduct, "id", id);
        return personalProduct;
    }

    private GiftProduct giftProduct(Long userId, Long id, Product product) {
        GiftProduct giftProduct = new GiftProduct(
                userId, product, new BigDecimal("0.900000"), "선물로 좋아요.", List.of("향기", "홈케어"));
        ReflectionTestUtils.setField(giftProduct, "id", id);
        return giftProduct;
    }
}
