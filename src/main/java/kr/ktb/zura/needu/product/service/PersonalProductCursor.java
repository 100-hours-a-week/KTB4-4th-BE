package kr.ktb.zura.needu.product.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.CommonErrorCode;
import kr.ktb.zura.needu.product.entity.PersonalProduct;

// 정렬 기준(score desc, id desc)의 마지막 값을 불투명한 문자열로 전달해, 클라이언트가 커서 내부 구조에 의존하지 않도록 한다.
public record PersonalProductCursor(BigDecimal score, Long id) {

    private static final String DELIMITER = ":";
    private static final int PART_COUNT = 2;

    public static PersonalProductCursor from(PersonalProduct personalProduct) {
        return new PersonalProductCursor(personalProduct.getScore(), personalProduct.getId());
    }

    public static PersonalProductCursor decode(String cursor) {
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = decoded.split(DELIMITER, -1);
            if (parts.length != PART_COUNT) {
                throw invalidCursor();
            }
            long id = Long.parseLong(parts[1]);
            if (id <= 0) {
                throw invalidCursor();
            }
            return new PersonalProductCursor(new BigDecimal(parts[0]), id);
        } catch (IllegalArgumentException e) {
            throw invalidCursor();
        }
    }

    public String encode() {
        String raw = score.toPlainString() + DELIMITER + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static BusinessException invalidCursor() {
        return new BusinessException(CommonErrorCode.COMMON_INVALID_REQUEST);
    }
}
