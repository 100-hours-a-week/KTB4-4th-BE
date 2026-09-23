package kr.ktb.zura.needu.feedback.controller;

import jakarta.validation.Valid;

import java.util.UUID;

import kr.ktb.zura.needu.common.response.ApiResponse;
import kr.ktb.zura.needu.feedback.dto.request.CreateErrorReportRequest;
import kr.ktb.zura.needu.feedback.dto.response.ErrorReportResponse;
import kr.ktb.zura.needu.feedback.service.ErrorReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/error-reports")
public class ErrorReportController {

    private final ErrorReportService errorReportService;

    @PostMapping
    public ResponseEntity<ApiResponse<ErrorReportResponse>> createErrorReport(
            @AuthenticationPrincipal Long userId,
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            @RequestBody @Valid CreateErrorReportRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(
                ErrorReportResponseMessages.CREATED,
                errorReportService.createErrorReport(userId, idempotencyKey, request)
        ));
    }
}
