package kr.ktb.zura.needu.product.controller;

import jakarta.validation.Valid;
import kr.ktb.zura.needu.product.dto.request.PersonalProductSearchCondition;
import kr.ktb.zura.needu.product.dto.response.PersonalProductResponse;
import kr.ktb.zura.needu.product.dto.response.ProductCursorApiResponse;
import kr.ktb.zura.needu.product.service.PersonalProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users/me/personal-recommendations")
public class PersonalProductController {

    private final PersonalProductService personalProductService;

    @GetMapping
    public ResponseEntity<ProductCursorApiResponse<PersonalProductResponse>> findAllPersonalProducts(
            @AuthenticationPrincipal Long userId,
            @Valid PersonalProductSearchCondition condition
    ) {
        return ResponseEntity.ok(ProductCursorApiResponse.of(
                ProductResponseMessages.PERSONAL_PRODUCTS_FOUND,
                personalProductService.findAllPersonalProducts(userId, condition)
        ));
    }
}
