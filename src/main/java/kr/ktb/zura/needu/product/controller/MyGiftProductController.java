package kr.ktb.zura.needu.product.controller;

import jakarta.validation.Valid;
import kr.ktb.zura.needu.product.dto.request.GiftProductSearchCondition;
import kr.ktb.zura.needu.product.dto.response.MyGiftProductResponse;
import kr.ktb.zura.needu.product.dto.response.ProductCursorApiResponse;
import kr.ktb.zura.needu.product.service.GiftProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users/me/gift-recommendations")
public class MyGiftProductController {

    private final GiftProductService giftProductService;

    @GetMapping
    public ResponseEntity<ProductCursorApiResponse<MyGiftProductResponse>> findAllMyGiftProducts(
            @AuthenticationPrincipal Long userId,
            @Valid GiftProductSearchCondition condition
    ) {
        return ResponseEntity.ok(ProductCursorApiResponse.of(
                ProductResponseMessages.MY_GIFT_PRODUCTS_FOUND,
                giftProductService.findAllMyGiftProducts(userId, condition)
        ));
    }
}
