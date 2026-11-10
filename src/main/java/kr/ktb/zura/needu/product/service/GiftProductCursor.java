package kr.ktb.zura.needu.product.service;

import java.math.BigDecimal;

import kr.ktb.zura.needu.common.cursor.CursorCodec;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.CommonErrorCode;
import kr.ktb.zura.needu.product.repository.GiftProductSummary;

// 정렬 기준(score desc, id desc)의 마지막 값을 불투명한 문자열로 전달해, 클라이언트가 커서 내부 구조에 의존하지 않도록 한다.
public record GiftProductCursor(BigDecimal score, Long id) {

    private static final String DELIMITER = ":";
    private static final int PART_COUNT = 2;

    public static GiftProductCursor from(GiftProductSummary giftProduct) {
        return new GiftProductCursor(giftProduct.score(), giftProduct.id());
    }

    public static GiftProductCursor decode(String cursor) {
        try {
            String decoded = CursorCodec.decode(cursor);
            String[] parts = decoded.split(DELIMITER, -1);
            if (parts.length != PART_COUNT) {
                throw invalidCursor();
            }
            long id = Long.parseLong(parts[1]);
            if (id <= 0) {
                throw invalidCursor();
            }
            return new GiftProductCursor(new BigDecimal(parts[0]), id);
        } catch (IllegalArgumentException e) {
            throw invalidCursor();
        }
    }

    public String encode() {
        String raw = score.toPlainString() + DELIMITER + id;
        return CursorCodec.encode(raw);
    }

    private static BusinessException invalidCursor() {
        return new BusinessException(CommonErrorCode.COMMON_INVALID_REQUEST);
    }
}
