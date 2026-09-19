package kr.ktb.zura.needu.product.dto.request;

public record GiftProductSearchCondition(long minPrice, long maxPrice, String cursor, int size) {
}
