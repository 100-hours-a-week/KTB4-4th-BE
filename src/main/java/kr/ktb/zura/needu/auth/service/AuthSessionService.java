package kr.ktb.zura.needu.auth.service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;
import kr.ktb.zura.needu.auth.entity.AuthSession;
import kr.ktb.zura.needu.auth.exception.AuthErrorCode;
import kr.ktb.zura.needu.auth.repository.AuthSessionRepository;
import kr.ktb.zura.needu.auth.repository.RefreshTokenReuseGraceRepository;
import kr.ktb.zura.needu.auth.type.RevokeReason;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.user.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class AuthSessionService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AuthSessionRepository authSessionRepository;
    private final RefreshTokenReuseGraceRepository reuseGraceRepository;
    private final UserService userService;
    private final Duration expiration;
    private final Duration reuseGrace;

    public AuthSessionService(AuthSessionRepository authSessionRepository,
                              RefreshTokenReuseGraceRepository reuseGraceRepository,
                              UserService userService,
                              @Value("${auth.refresh-token-expiration}") Duration expiration,
                              @Value("${auth.refresh-token-reuse-grace}") Duration reuseGrace) {
        if (expiration.isZero() || expiration.isNegative()) {
            throw new IllegalArgumentException("Refresh token expiration must be positive");
        }
        if (reuseGrace.isNegative()) {
            throw new IllegalArgumentException("Refresh token reuse grace must not be negative");
        }
        this.authSessionRepository = authSessionRepository;
        this.reuseGraceRepository = reuseGraceRepository;
        this.userService = userService;
        this.expiration = expiration;
        this.reuseGrace = reuseGrace;
    }

    @Transactional
    public RefreshToken create(Long userId) {
        String token = generateToken();
        LocalDateTime expiresAt = LocalDateTime.now().plus(expiration);
        authSessionRepository.save(new AuthSession(userId, hash(token), expiresAt));
        return new RefreshToken(userId, token, expiration);
    }

    // 동시 재발급 경합에서 이미 교체된 토큰으로 늦게 도착한 요청은 유예 시간 동안
    // 리프레시 토큰을 다시 교체하지 않고 액세스 토큰 재발급만 허용한다.
    // 먼저 처리된 요청이 내려준 새 리프레시 쿠키를 덮어쓰지 않기 위함이다.
    @Transactional
    public RefreshToken rotate(String token) {
        byte[] tokenHash = hash(token);
        AuthSession session = authSessionRepository.findByRefreshTokenHash(tokenHash).orElse(null);
        if (session == null) {
            return reuseWithinGrace(tokenHash);
        }
        validateUsableSession(session);
        String nextToken = generateToken();
        session.rotateRefreshToken(hash(nextToken));
        // 같은 토큰으로 행 잠금을 기다리던 요청이 커밋 직후 유예 기록을 찾을 수 있도록 커밋 전에 저장한다
        saveReuseGrace(tokenHash, session.getId());
        Duration remaining = Duration.between(LocalDateTime.now(), session.getRefreshExpiresAt());
        return new RefreshToken(session.getUserId(), nextToken, remaining);
    }

    private RefreshToken reuseWithinGrace(byte[] tokenHash) {
        Long sessionId = findReuseGraceSessionId(tokenHash)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.AUTH_REFRESH_TOKEN_INVALID));
        AuthSession session = authSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.AUTH_REFRESH_TOKEN_INVALID));
        validateUsableSession(session);
        log.info("Rotated refresh token reused within grace. sessionId={}", sessionId);
        return RefreshToken.notRotated(session.getUserId());
    }

    private void validateUsableSession(AuthSession session) {
        if (session.isRevoked() || session.isExpired()) {
            throw new BusinessException(AuthErrorCode.AUTH_REFRESH_TOKEN_INVALID);
        }
        userService.validateAuthenticatableUser(session.getUserId());
    }

    // Redis 장애 시 유예만 적용되지 않고 기존 동작(늦은 요청 401)으로 돌아간다
    private void saveReuseGrace(byte[] rotatedTokenHash, Long sessionId) {
        if (reuseGrace.isZero()) {
            return;
        }
        try {
            reuseGraceRepository.save(rotatedTokenHash, sessionId, reuseGrace);
        } catch (DataAccessException exception) {
            log.warn("Failed to save refresh token reuse grace. sessionId={}", sessionId, exception);
        }
    }

    private Optional<Long> findReuseGraceSessionId(byte[] tokenHash) {
        try {
            return reuseGraceRepository.findSessionId(tokenHash);
        } catch (DataAccessException exception) {
            log.warn("Failed to find refresh token reuse grace.", exception);
            return Optional.empty();
        }
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

    @Transactional
    public void revokeAllByUserId(Long userId, RevokeReason reason) {
        authSessionRepository.findAllByUserIdAndRevokedAtIsNull(userId)
                .forEach(session -> session.revoke(reason));
    }

    @Transactional
    public void deleteAllByUserId(Long userId) {
        authSessionRepository.deleteAllByUserId(userId);
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

    // value가 null이면 리프레시 토큰을 교체하지 않았으므로 클라이언트의 기존 쿠키를 유지해야 한다
    public record RefreshToken(Long userId, String value, Duration remaining) {

        static RefreshToken notRotated(Long userId) {
            return new RefreshToken(userId, null, null);
        }
    }
}
