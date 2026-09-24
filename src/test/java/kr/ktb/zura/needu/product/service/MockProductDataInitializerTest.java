package kr.ktb.zura.needu.product.service;

import java.util.Optional;

import kr.ktb.zura.needu.product.entity.Product;
import kr.ktb.zura.needu.product.repository.ProductRepository;
import kr.ktb.zura.needu.product.type.PlatformType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MockProductDataInitializerTest {

    @Mock
    private ProductRepository productRepository;

    @Test
    void missingMockProducts_run_savesProductsUsedByConfirmResponse() {
        given(productRepository.findByPlatformTypeAndExternalId(PlatformType.COUPANG, "88213"))
                .willReturn(Optional.empty());
        given(productRepository.findByPlatformTypeAndExternalId(PlatformType.COUPANG, "88214"))
                .willReturn(Optional.empty());
        MockProductDataInitializer initializer = new MockProductDataInitializer(productRepository);

        initializer.run(null);

        ArgumentCaptor<Product> products = ArgumentCaptor.forClass(Product.class);
        verify(productRepository, times(2)).save(products.capture());
        assertThat(products.getAllValues())
                .extracting(Product::getExternalId)
                .containsExactly("88213", "88214");
    }
}
