package kr.ktb.zura.needu.product.exception;

import kr.ktb.zura.needu.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ProductErrorCode implements ErrorCode {

    PRODUCT_GIFT_RECOMMENDATION_FORBIDDEN(HttpStatus.FORBIDDEN, "해당 친구의 추천 상품을 조회할 수 없습니다.");

    private final HttpStatus status;
    private final String message;
}
