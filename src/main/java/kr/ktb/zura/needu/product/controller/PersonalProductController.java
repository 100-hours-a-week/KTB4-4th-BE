package kr.ktb.zura.needu.product.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import kr.ktb.zura.needu.common.response.CursorApiResponse;
import kr.ktb.zura.needu.product.dto.request.PersonalProductSearchCondition;
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

    private final PersonalProductService personalProductService;

    @GetMapping
    public ResponseEntity<CursorApiResponse<PersonalProductResponse>> findAllPersonalProducts(
            @AuthenticationPrincipal Long userId,
            @RequestParam @PositiveOrZero long minPrice,
            @RequestParam @PositiveOrZero long maxPrice,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = PersonalProductPageLimits.DEFAULT_PAGE_SIZE)
            @Min(PersonalProductPageLimits.MIN_PAGE_SIZE) @Max(PersonalProductPageLimits.MAX_PAGE_SIZE) int size
    ) {
        PersonalProductSearchCondition condition = new PersonalProductSearchCondition(minPrice, maxPrice, cursor, size);
        return ResponseEntity.ok(CursorApiResponse.of(
                ProductResponseMessages.PERSONAL_PRODUCTS_FOUND,
                personalProductService.findAllPersonalProducts(userId, condition)
        ));
    }
}
