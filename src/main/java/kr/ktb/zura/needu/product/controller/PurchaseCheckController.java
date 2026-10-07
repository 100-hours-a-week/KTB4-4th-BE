package kr.ktb.zura.needu.product.controller;

import jakarta.validation.Valid;
import kr.ktb.zura.needu.common.response.ApiResponse;
import kr.ktb.zura.needu.product.dto.request.SubmitPurchaseCheckRequest;
import kr.ktb.zura.needu.product.dto.response.PurchaseCheckResponse;
import kr.ktb.zura.needu.product.service.PurchaseCheckService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/purchase-checks")
public class PurchaseCheckController {

    private final PurchaseCheckService purchaseCheckService;

    @PostMapping("/{purchaseCheckId}")
    public ResponseEntity<ApiResponse<PurchaseCheckResponse>> answerPurchaseCheck(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long purchaseCheckId,
            @Valid @RequestBody SubmitPurchaseCheckRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.of(
                ProductResponseMessages.PURCHASE_CHECK_ANSWERED,
                purchaseCheckService.answerPurchaseCheck(userId, purchaseCheckId, request.purchased())
        ));
    }
}
