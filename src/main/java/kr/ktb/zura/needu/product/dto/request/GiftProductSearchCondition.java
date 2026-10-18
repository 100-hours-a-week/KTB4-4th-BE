package kr.ktb.zura.needu.product.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;

public record GiftProductSearchCondition(
        @Schema(defaultValue = "0")
        @PositiveOrZero
        Long minPrice,
        @Schema(defaultValue = "99999999")
        @PositiveOrZero
        Long maxPrice,
        String category,
        String cursor,
        @Schema(defaultValue = "20")
        @Min(GiftProductSearchCondition.MIN_PAGE_SIZE)
        @Max(GiftProductSearchCondition.MAX_PAGE_SIZE)
        Integer size
) {

    private static final long DEFAULT_MIN_PRICE = 0L;
    private static final long DEFAULT_MAX_PRICE = 99_999_999L;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MIN_PAGE_SIZE = 1;
    private static final int MAX_PAGE_SIZE = 50;

    public GiftProductSearchCondition {
        minPrice = minPrice == null ? DEFAULT_MIN_PRICE : minPrice;
        maxPrice = maxPrice == null ? DEFAULT_MAX_PRICE : maxPrice;
        size = size == null ? DEFAULT_PAGE_SIZE : size;
    }
}
