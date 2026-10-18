package kr.ktb.zura.needu.user.repository;

import java.util.List;
import java.util.Optional;
import kr.ktb.zura.needu.user.entity.UserConsent;
import kr.ktb.zura.needu.user.type.ConsentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserConsentRepository extends JpaRepository<UserConsent, Long> {

    @Query("""
            select consent from UserConsent consent
            where consent.userId = :userId
              and consent.id in (
                  select max(latest.id) from UserConsent latest
                  where latest.userId = :userId
                  group by latest.consentType
              )
            """)
    List<UserConsent> findAllLatestByUserId(@Param("userId") Long userId);

    Optional<UserConsent> findFirstByUserIdAndConsentTypeOrderByIdDesc(Long userId, ConsentType consentType);
}
