package kr.ktb.zura.needu.anonymouschat.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record AnonymousMessageSearchCondition(
        String cursor,
        @Min(AnonymousMessageSearchCondition.MIN_PAGE_SIZE)
        @Max(AnonymousMessageSearchCondition.MAX_PAGE_SIZE)
        Integer size
) {

    private static final int MIN_PAGE_SIZE = 1;
    private static final int MAX_PAGE_SIZE = 50;
    private static final int DEFAULT_PAGE_SIZE = 20;

    public AnonymousMessageSearchCondition {
        if (size == null) {
            size = DEFAULT_PAGE_SIZE;
        }
    }
}
