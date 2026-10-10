package kr.ktb.zura.needu.product.dto.response;

import kr.ktb.zura.needu.product.type.ProductCategory;

public record ProductCategoryResponse(String code, String name) {

    public static ProductCategoryResponse from(ProductCategory category) {
        return new ProductCategoryResponse(category.name(), category.getDisplayName());
    }
}
