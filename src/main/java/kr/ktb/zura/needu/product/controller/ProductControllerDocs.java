package kr.ktb.zura.needu.product.controller;

import java.util.List;
import kr.ktb.zura.needu.common.openapi.ControllerDocs;
import kr.ktb.zura.needu.common.openapi.OperationDoc;
import kr.ktb.zura.needu.friend.exception.FriendErrorCode;
import kr.ktb.zura.needu.product.exception.ProductErrorCode;
import kr.ktb.zura.needu.user.exception.UserErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ProductControllerDocs implements ControllerDocs {

    // 추천 상품 조회 전 활성 사용자인지 확인할 때 발생
    private static final List<UserErrorCode> ACTIVE_USER_ERRORS = List.of(
            UserErrorCode.USER_NOT_FOUND,
            UserErrorCode.USER_WITHDRAWN,
            UserErrorCode.USER_BLOCKED,
            UserErrorCode.USER_ONBOARDING_REQUIRED);

    @Override
    public List<OperationDoc> operations() {
        return List.of(
                OperationDoc.of(PersonalProductController.class, "findAllPersonalProducts")
                        .errors(ACTIVE_USER_ERRORS)
                        .build(),
                OperationDoc.of(GiftProductController.class, "findAllGiftProducts")
                        .errors(ACTIVE_USER_ERRORS)
                        .errors(FriendErrorCode.FRIEND_NOT_FOUND,
                                ProductErrorCode.PRODUCT_GIFT_RECOMMENDATION_FORBIDDEN)
                        .build(),
                OperationDoc.of(MyGiftProductController.class, "findAllMyGiftProducts")
                        .errors(ACTIVE_USER_ERRORS)
                        .build(),
                OperationDoc.of(ProductController.class, "findProduct")
                        .errors(UserErrorCode.USER_BLOCKED, UserErrorCode.USER_ONBOARDING_REQUIRED,
                                ProductErrorCode.PRODUCT_GIFT_RECOMMENDATION_FORBIDDEN,
                                ProductErrorCode.PRODUCT_RECOMMENDATION_NOT_FOUND, FriendErrorCode.FRIEND_NOT_FOUND)
                        .build(),
                OperationDoc.of(ProductController.class, "updateFeedback")
                        .errors(ACTIVE_USER_ERRORS)
                        .errors(ProductErrorCode.PRODUCT_RECOMMENDATION_NOT_FOUND,
                                ProductErrorCode.PRODUCT_FEEDBACK_ALREADY_DISLIKED)
                        .build(),
                OperationDoc.of(ProductLinkClickController.class, "createLinkClick")
                        .successStatus(HttpStatus.NO_CONTENT)
                        .errors(ACTIVE_USER_ERRORS)
                        .build(),
                OperationDoc.of(PurchaseCheckController.class, "answerPurchaseCheck")
                        .errors(ACTIVE_USER_ERRORS)
                        .errors(ProductErrorCode.PURCHASE_CHECK_NOT_FOUND,
                                ProductErrorCode.PURCHASE_CHECK_ALREADY_ANSWERED)
                        .build()
        );
    }
}
