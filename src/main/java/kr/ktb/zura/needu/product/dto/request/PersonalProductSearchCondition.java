package kr.ktb.zura.needu.product.dto.request;

public record PersonalProductSearchCondition(long minPrice, long maxPrice, String cursor, int size) {
}
