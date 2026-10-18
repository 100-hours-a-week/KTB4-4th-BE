package kr.ktb.zura.needu.user.dto.request;

import jakarta.validation.constraints.NotNull;

public record ConsentAgreementRequest(@NotNull Long id, @NotNull Boolean agreed) {
}
