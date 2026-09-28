package kr.ktb.zura.needu.product.dto.response;

import java.util.List;

public record ProductCursorPageResponse<T>(
        List<T> items,
        PriceRangeResponse priceRange,
        String nextCursor,
        boolean hasNext
) {
}
