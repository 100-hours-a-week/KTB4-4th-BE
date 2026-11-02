package kr.ktb.zura.needu.product.type;

public enum ProductActivityType {
    LIKE,
    DISLIKE,
    PURCHASED,
    RECEIVED;

    public static ProductActivityType from(ProductFeedbackType feedbackType) {
        return switch (feedbackType) {
            case LIKE -> LIKE;
            case DISLIKE -> DISLIKE;
        };
    }

    public static ProductActivityType fromPurchaseContext(ProductContext context) {
        return context == ProductContext.FRIEND_GIFT ? RECEIVED : PURCHASED;
    }
}
