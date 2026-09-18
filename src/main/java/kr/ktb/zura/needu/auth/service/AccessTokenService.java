package kr.ktb.zura.needu.auth.service;

import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

@Service
public class AccessTokenService {

    private final JwtEncoder jwtEncoder;
    private final Duration expiration;

    public AccessTokenService(JwtEncoder jwtEncoder,
                              @Value("${auth.jwt.access-token-expiration}") Duration expiration) {
        if (expiration.isZero() || expiration.isNegative()) {
            throw new IllegalArgumentException("JWT access token expiration must be positive");
        }
        this.jwtEncoder = jwtEncoder;
        this.expiration = expiration;
    }

    public String issueAccessToken(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .claim("userId", userId)
                .issuedAt(now)
                .expiresAt(now.plus(expiration))
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
