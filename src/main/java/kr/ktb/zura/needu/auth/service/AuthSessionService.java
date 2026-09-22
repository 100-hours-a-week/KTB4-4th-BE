package kr.ktb.zura.needu.auth.service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import kr.ktb.zura.needu.auth.entity.AuthSession;
import kr.ktb.zura.needu.auth.exception.AuthErrorCode;
import kr.ktb.zura.needu.auth.repository.AuthSessionRepository;
import kr.ktb.zura.needu.auth.type.RevokeReason;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.user.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthSessionService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AuthSessionRepository authSessionRepository;
    private final UserService userService;
    private final Duration expiration;

    public AuthSessionService(AuthSessionRepository authSessionRepository, UserService userService,
                              @Value("${auth.refresh-token-expiration}") Duration expiration) {
        if (expiration.isZero() || expiration.isNegative()) {
            throw new IllegalArgumentException("Refresh token expiration must be positive");
        }
        this.authSessionRepository = authSessionRepository;
        this.userService = userService;
        this.expiration = expiration;
    }

    @Transactional
    public RefreshToken create(Long userId) {
        String token = generateToken();
        LocalDateTime expiresAt = LocalDateTime.now().plus(expiration);
        authSessionRepository.save(new AuthSession(userId, hash(token), expiresAt));
        return new RefreshToken(userId, token, expiration);
    }

    @Transactional
    public RefreshToken rotate(String token) {
        AuthSession session = authSessionRepository.findByRefreshTokenHash(hash(token))
                .orElseThrow(() -> new BusinessException(AuthErrorCode.AUTH_REFRESH_TOKEN_INVALID));
        if (session.isRevoked() || session.isExpired()) {
            throw new BusinessException(AuthErrorCode.AUTH_REFRESH_TOKEN_INVALID);
        }
        userService.validateAuthenticatableUser(session.getUserId());
        String nextToken = generateToken();
        session.rotateRefreshToken(hash(nextToken));
        Duration remaining = Duration.between(LocalDateTime.now(), session.getRefreshExpiresAt());
        return new RefreshToken(session.getUserId(), nextToken, remaining);
    }

    @Transactional
    public void revoke(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        try {
            authSessionRepository.findByRefreshTokenHash(hash(token))
                    .ifPresent(session -> session.revoke(RevokeReason.LOGOUT));
        } catch (BusinessException ignored) {
            // 잘못된 쿠키라도 로그아웃 응답에서 쿠키를 지울 수 있어야 한다.
        }
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private byte[] hash(String token) {
        if (token == null || token.length() != 43) {
            throw new BusinessException(AuthErrorCode.AUTH_REFRESH_TOKEN_INVALID);
        }
        try {
            if (Base64.getUrlDecoder().decode(token).length != 32) {
                throw new BusinessException(AuthErrorCode.AUTH_REFRESH_TOKEN_INVALID);
            }
            return MessageDigest.getInstance("SHA-256").digest(token.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(AuthErrorCode.AUTH_REFRESH_TOKEN_INVALID);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record RefreshToken(Long userId, String value, Duration remaining) {
    }
}
