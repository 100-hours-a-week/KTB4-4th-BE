package kr.ktb.zura.needu.product.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;

import kr.ktb.zura.needu.common.response.CursorApiResponse;
import kr.ktb.zura.needu.product.dto.request.GiftProductSearchCondition;
import kr.ktb.zura.needu.product.dto.response.GiftProductResponse;
import kr.ktb.zura.needu.product.service.GiftProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users/{userId}/gift-recommendations")
public class GiftProductController {

    private final GiftProductService giftProductService;

    @GetMapping
    public ResponseEntity<CursorApiResponse<GiftProductResponse>> findAllGiftProducts(
            @AuthenticationPrincipal Long loginUserId,
            @PathVariable Long userId,
            @RequestParam @PositiveOrZero long minPrice,
            @RequestParam @PositiveOrZero long maxPrice,
            @RequestParam(required = false) String cursor,
            @RequestParam @Min(GiftProductPageLimits.MIN_PAGE_SIZE) @Max(GiftProductPageLimits.MAX_PAGE_SIZE) int size
    ) {
        GiftProductSearchCondition condition = new GiftProductSearchCondition(minPrice, maxPrice, cursor, size);
        return ResponseEntity.ok(CursorApiResponse.of(
                ProductResponseMessages.GIFT_PRODUCTS_FOUND,
                giftProductService.findAllGiftProducts(loginUserId, userId, condition)
        ));
    }
}
