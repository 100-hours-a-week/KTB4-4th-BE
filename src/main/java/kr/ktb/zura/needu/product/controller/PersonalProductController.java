package kr.ktb.zura.needu.product.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import kr.ktb.zura.needu.common.response.CursorApiResponse;
import kr.ktb.zura.needu.product.dto.response.PersonalProductResponse;
import kr.ktb.zura.needu.product.service.PersonalProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users/me/personal-recommendations")
public class PersonalProductController {

    private static final String PERSONAL_PRODUCTS_FOUND_MESSAGE = "개인 추천 상품 목록을 조회했습니다.";
    private static final String DEFAULT_PAGE_SIZE = "20";
    private static final int MIN_PAGE_SIZE = 1;
    private static final int MAX_PAGE_SIZE = 50;

    private final PersonalProductService personalProductService;

    @GetMapping
    public ResponseEntity<CursorApiResponse<PersonalProductResponse>> findAllPersonalProducts(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = DEFAULT_PAGE_SIZE) @Min(MIN_PAGE_SIZE) @Max(MAX_PAGE_SIZE) int size
    ) {
        return ResponseEntity.ok(CursorApiResponse.of(
                PERSONAL_PRODUCTS_FOUND_MESSAGE,
                personalProductService.findAllPersonalProducts(userId, cursor, size)
        ));
    }
}
