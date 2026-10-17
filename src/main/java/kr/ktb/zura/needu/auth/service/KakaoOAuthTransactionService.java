package kr.ktb.zura.needu.auth.service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import kr.ktb.zura.needu.auth.repository.KakaoOAuthTransactionRepository;
import kr.ktb.zura.needu.auth.type.KakaoOAuthContext;
import kr.ktb.zura.needu.auth.type.KakaoOAuthFlow;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class KakaoOAuthTransactionService {

    private final KakaoOAuthTransactionRepository kakaoOAuthTransactionRepository;
    @Getter
    private final Duration ttl;

    public KakaoOAuthTransactionService(
            KakaoOAuthTransactionRepository repository,
            @Value("${kakao.oauth.transaction-ttl}") Duration ttl
    ) {
        this.kakaoOAuthTransactionRepository = repository;
        this.ttl = ttl;
    }

    public String createLoginState(String returnUrl) {
        return create(KakaoOAuthFlow.LOGIN, returnUrl, null);
    }

    public String createFriendState(Long userId, String returnUrl) {
        return create(KakaoOAuthFlow.FRIEND, returnUrl, userId);
    }

    public Optional<KakaoOAuthContext> consumeLoginState(String state, String cookieState) {
        return consume(KakaoOAuthFlow.LOGIN, state, cookieState);
    }

    public Optional<KakaoOAuthContext> consumeFriendState(String state, String cookieState) {
        return consume(KakaoOAuthFlow.FRIEND, state, cookieState);
    }

    private String create(KakaoOAuthFlow flow, String returnUrl, Long userId) {
        String state = UUID.randomUUID().toString();
        kakaoOAuthTransactionRepository.save(
                flow, state, new KakaoOAuthContext(returnUrl, userId), ttl);
        return state;
    }

    private Optional<KakaoOAuthContext> consume(
            KakaoOAuthFlow flow,
            String state,
            String cookieState
    ) {
        if (state == null || state.isBlank() || cookieState == null || cookieState.isBlank()
                || !state.equals(cookieState)) {
            return Optional.empty();
        }
        return kakaoOAuthTransactionRepository.consume(flow, state);
    }
}
