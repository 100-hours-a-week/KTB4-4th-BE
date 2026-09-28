package kr.ktb.zura.needu.product.dto.response;

public record ProductCursorApiResponse<T>(
        String message,
        ProductListResponse<T> data,
        String nextCursor,
        boolean hasNext
) {

    public static <T> ProductCursorApiResponse<T> of(String message, ProductCursorPageResponse<T> page) {
        return new ProductCursorApiResponse<>(
                message,
                new ProductListResponse<>(page.items(), page.priceRange()),
                page.nextCursor(),
                page.hasNext()
        );
    }
}
