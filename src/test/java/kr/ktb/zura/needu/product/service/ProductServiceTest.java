package kr.ktb.zura.needu.product.service;

import kr.ktb.zura.needu.product.dto.response.ProductCategoryResponse;
import kr.ktb.zura.needu.product.type.ProductCategory;
import kr.ktb.zura.needu.user.service.UserService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;

class ProductServiceTest {

    private final ProductService productService = new ProductService(mock(UserService.class));

    @Test
    void categoriesDefined_findAllCategories_returnsAllInTabOrder() {
        assertThat(productService.findAllCategories())
                .hasSize(ProductCategory.values().length)
                .extracting(ProductCategoryResponse::code, ProductCategoryResponse::name)
                .startsWith(
                        tuple("VOUCHER", "교환권"),
                        tuple("LIVING", "리빙"));
    }
}
