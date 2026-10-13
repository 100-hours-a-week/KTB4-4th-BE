package kr.ktb.zura.needu.auth.repository;

import java.util.Optional;
import kr.ktb.zura.needu.auth.entity.KakaoToken;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KakaoTokenRepository extends JpaRepository<KakaoToken, Long> {

    Optional<KakaoToken> findByUserId(Long userId);
}
