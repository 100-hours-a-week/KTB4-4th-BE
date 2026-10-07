package kr.ktb.zura.needu.product.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import kr.ktb.zura.needu.common.response.ApiResponse;
import kr.ktb.zura.needu.common.response.ItemsResponse;
import kr.ktb.zura.needu.product.dto.request.UpdateProductFeedbackRequest;
import kr.ktb.zura.needu.product.dto.response.ProductCategoryResponse;
import kr.ktb.zura.needu.product.dto.response.ProductDetailResponse;
import kr.ktb.zura.needu.product.dto.response.ProductFeedbackResponse;
import kr.ktb.zura.needu.product.service.ProductFeedbackService;
import kr.ktb.zura.needu.product.service.ProductService;
import kr.ktb.zura.needu.product.type.ProductContext;
import kr.ktb.zura.needu.product.type.ProductFeedbackType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductService productService;
    private final ProductFeedbackService productFeedbackService;

    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<ItemsResponse<ProductCategoryResponse>>> findAllCategories() {
        return ResponseEntity.ok(ApiResponse.of(
                ProductResponseMessages.CATEGORIES_FOUND,
                new ItemsResponse<>(productService.findAllCategories())
        ));
    }

    @GetMapping("/{productId}")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> findProduct(
            @AuthenticationPrincipal Long loginUserId,
            @PathVariable Long productId,
            @RequestParam(required = false) @NotNull @Pattern(regexp = "PERSONAL|MY_GIFT|FRIEND_GIFT") String context,
            @RequestParam(required = false) Long userId
    ) {
        return ResponseEntity.ok(ApiResponse.of(
                ProductResponseMessages.PRODUCT_FOUND,
                productService.findProduct(loginUserId, productId, ProductContext.valueOf(context), userId)
        ));
    }

    @PutMapping("/{productId}/feedback")
    public ResponseEntity<ApiResponse<ProductFeedbackResponse>> updateFeedback(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long productId,
            @Valid @RequestBody UpdateProductFeedbackRequest request
    ) {
        ProductFeedbackType feedback =
                request.feedback() == null ? null : ProductFeedbackType.valueOf(request.feedback());
        return ResponseEntity.ok(ApiResponse.of(
                ProductResponseMessages.FEEDBACK_SAVED,
                productFeedbackService.updateFeedback(
                        userId, productId, ProductContext.valueOf(request.context()), feedback)
        ));
    }
}
