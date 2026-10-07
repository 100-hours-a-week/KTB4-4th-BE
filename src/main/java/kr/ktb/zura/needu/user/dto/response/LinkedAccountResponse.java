package kr.ktb.zura.needu.user.dto.response;

import kr.ktb.zura.needu.user.type.AccountLinkStatus;
import kr.ktb.zura.needu.user.type.AccountProvider;

public record LinkedAccountResponse(AccountProvider provider, AccountLinkStatus status) {
}
