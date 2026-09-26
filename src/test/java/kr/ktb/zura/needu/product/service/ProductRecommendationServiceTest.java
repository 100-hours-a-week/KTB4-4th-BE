package kr.ktb.zura.needu.product.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerRecommendationResult;
import kr.ktb.zura.needu.aichat.client.dto.response.AiServerRecommendedItem;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.product.entity.GiftProduct;
import kr.ktb.zura.needu.product.entity.PersonalProduct;
import kr.ktb.zura.needu.product.entity.Product;
import kr.ktb.zura.needu.product.exception.ProductErrorCode;
import kr.ktb.zura.needu.product.repository.GiftProductRepository;
import kr.ktb.zura.needu.product.repository.PersonalProductRepository;
import kr.ktb.zura.needu.product.repository.ProductRepository;
import kr.ktb.zura.needu.product.type.PlatformType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProductRecommendationServiceTest {

    private static final Long USER_ID = 1L;

    private long nextProductId = 1L;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private PersonalProductRepository personalProductRepository;

    @Mock
    private GiftProductRepository giftProductRepository;

    @Captor
    private ArgumentCaptor<List<PersonalProduct>> personalProductsCaptor;

    @Captor
    private ArgumentCaptor<List<GiftProduct>> giftProductsCaptor;

    @InjectMocks
    private ProductRecommendationService productRecommendationService;

    @Test
    void selfAndGiftItems_saveRecommendations_savesEachRecommendationType() {
        Product selfProduct = product("self-1");
        Product secondSelfProduct = product("self-2");
        Product giftProduct = product("gift-1");
        givenProduct("self-1", selfProduct);
        givenProduct("self-2", secondSelfProduct);
        givenProduct("gift-1", giftProduct);

        productRecommendationService.saveRecommendations(
                USER_ID,
                recommendations(item("self-1", "첫 번째"), item("self-2", "두 번째")),
                recommendations(item("gift-1", "선물 추천")),
                List.of("캠핑"));

        verify(personalProductRepository).saveAll(personalProductsCaptor.capture());
        verify(giftProductRepository).saveAll(giftProductsCaptor.capture());
        assertThat(personalProductsCaptor.getValue())
                .extracting(PersonalProduct::getProduct)
                .containsExactly(selfProduct, secondSelfProduct);
        assertThat(personalProductsCaptor.getValue())
                .extracting(PersonalProduct::getScore)
                .containsExactly(new BigDecimal("9.2"), new BigDecimal("8.7"));
        GiftProduct savedGift = giftProductsCaptor.getValue().getFirst();
        assertThat(savedGift.getProduct()).isSameAs(giftProduct);
        assertThat(savedGift.getReason()).isEqualTo("선물 추천");
        assertThat(savedGift.getTasteKeywords()).containsExactly("캠핑");
    }

    @Test
    void unknownProduct_saveRecommendations_doesNotSavePartialRecommendations() {
        Product selfProduct = product("self-1");
        givenProduct("self-1", selfProduct);
        given(productRepository.findByPlatformTypeAndExternalId(PlatformType.COUPANG, "gift-1"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> productRecommendationService.saveRecommendations(
                USER_ID,
                recommendations(item("self-1", "나를 위한 추천")),
                recommendations(item("gift-1", "선물 추천")),
                List.of()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ProductErrorCode.PRODUCT_NOT_FOUND);
        verify(personalProductRepository, never()).saveAll(org.mockito.ArgumentMatchers.any());
        verify(giftProductRepository, never()).saveAll(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void personalProductAlreadyRecommended_saveRecommendations_updatesScoreWithoutNewRow() {
        Product selfProduct = product("self-1");
        givenProduct("self-1", selfProduct);
        PersonalProduct existing = new PersonalProduct(USER_ID, selfProduct, new BigDecimal("1.0"), "이전 추천");
        given(personalProductRepository.findAllByUserIdAndProductIdIn(eq(USER_ID), anyCollection()))
                .willReturn(List.of(existing));

        productRecommendationService.saveRecommendations(
                USER_ID, recommendations(item("self-1", "새 추천")), recommendations(), List.of());

        verify(personalProductRepository).saveAll(personalProductsCaptor.capture());
        assertThat(personalProductsCaptor.getValue()).isEmpty();
        assertThat(existing.getScore()).isEqualTo(new BigDecimal("9.2"));
        assertThat(existing.getReason()).isEqualTo("이전 추천");
    }

    @Test
    void giftProductAlreadyRecommended_saveRecommendations_updatesScoreAndSavesOnlyNewProducts() {
        Product existingGiftProduct = product("self-1");
        Product newGiftProduct = product("gift-1");
        givenProduct("self-1", existingGiftProduct);
        givenProduct("gift-1", newGiftProduct);
        GiftProduct existing = new GiftProduct(
                USER_ID, existingGiftProduct, new BigDecimal("1.0"), "이전 추천", List.of("독서"));
        given(giftProductRepository.findAllByUserIdAndProductIdIn(eq(USER_ID), anyCollection()))
                .willReturn(List.of(existing));

        productRecommendationService.saveRecommendations(
                USER_ID,
                recommendations(),
                recommendations(item("self-1", "새 추천"), item("gift-1", "선물 추천")),
                List.of("캠핑"));

        verify(giftProductRepository).saveAll(giftProductsCaptor.capture());
        assertThat(giftProductsCaptor.getValue())
                .extracting(GiftProduct::getProduct)
                .containsExactly(newGiftProduct);
        assertThat(existing.getScore()).isEqualTo(new BigDecimal("9.2"));
        assertThat(existing.getTasteKeywords()).containsExactly("독서");
    }

    @Test
    void sameProductTwiceInResponse_saveRecommendations_savesOneRowWithLastScore() {
        Product selfProduct = product("self-1");
        givenProduct("self-1", selfProduct);
        AiServerRecommendedItem first = item("self-1", "첫 번째");
        AiServerRecommendedItem second =
                new AiServerRecommendedItem(PlatformType.COUPANG, "self-1", new BigDecimal("3.1"), "두 번째");

        productRecommendationService.saveRecommendations(
                USER_ID, recommendations(first, second), recommendations(), List.of());

        verify(personalProductRepository).saveAll(personalProductsCaptor.capture());
        assertThat(personalProductsCaptor.getValue()).singleElement()
                .extracting(PersonalProduct::getScore)
                .isEqualTo(new BigDecimal("3.1"));
    }

    private void givenProduct(String externalId, Product product) {
        given(productRepository.findByPlatformTypeAndExternalId(PlatformType.COUPANG, externalId))
                .willReturn(Optional.of(product));
    }

    private AiServerRecommendationResult recommendations(AiServerRecommendedItem... items) {
        return new AiServerRecommendationResult(List.of(items));
    }

    private AiServerRecommendedItem item(String externalId, String reason) {
        BigDecimal score = externalId.equals("self-1") ? new BigDecimal("9.2") : new BigDecimal("8.7");
        return new AiServerRecommendedItem(PlatformType.COUPANG, externalId, score, reason);
    }

    private Product product(String externalId) {
        Product product = new Product(
                PlatformType.COUPANG, externalId, "상품", null, null,
                BigDecimal.ONE, null, null, null);
        ReflectionTestUtils.setField(product, "id", nextProductId++);
        return product;
    }
}
