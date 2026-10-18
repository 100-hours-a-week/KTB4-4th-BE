package kr.ktb.zura.needu.user.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

// 필수 항목 누락과 없는 항목 id는, Service에서 검증
public record UpdateConsentRequest(@NotEmpty List<@NotNull @Valid ConsentAgreementRequest> consents) {
}
