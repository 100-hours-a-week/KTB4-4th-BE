package kr.ktb.zura.needu.product.exception;

import kr.ktb.zura.needu.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ProductErrorCode implements ErrorCode {

    PRODUCT_GIFT_RECOMMENDATION_FORBIDDEN(HttpStatus.FORBIDDEN, "해당 친구의 추천 상품을 조회할 수 없습니다."),
    PRODUCT_RECOMMENDATION_NOT_FOUND(HttpStatus.NOT_FOUND, "추천 상품을 찾을 수 없습니다."),
    PRODUCT_FEEDBACK_ALREADY_DISLIKED(HttpStatus.CONFLICT, "별로예요를 선택한 상품은 평가를 바꿀 수 없습니다."),
    PURCHASE_CHECK_NOT_FOUND(HttpStatus.NOT_FOUND, "구매 확인 요청을 찾을 수 없습니다."),
    PURCHASE_CHECK_ALREADY_ANSWERED(HttpStatus.CONFLICT, "이미 구매 여부를 답했습니다.");

    private final HttpStatus status;
    private final String message;
}
