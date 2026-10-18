package kr.ktb.zura.needu.product.controller;

import jakarta.validation.Valid;
import kr.ktb.zura.needu.product.dto.request.CreateProductLinkClickRequest;
import kr.ktb.zura.needu.product.service.ProductLinkClickService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/analytics/product-link-clicks")
public class ProductLinkClickController {

    private final ProductLinkClickService productLinkClickService;

    @PostMapping
    public ResponseEntity<Void> createLinkClick(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody CreateProductLinkClickRequest request
    ) {
        productLinkClickService.createLinkClick(userId, request);
        return ResponseEntity.noContent().build();
    }
}
