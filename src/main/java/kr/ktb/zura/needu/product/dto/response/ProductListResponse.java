package kr.ktb.zura.needu.product.dto.response;

import java.util.List;

public record ProductListResponse<T>(List<T> items, PriceRangeResponse priceRange) {
}
