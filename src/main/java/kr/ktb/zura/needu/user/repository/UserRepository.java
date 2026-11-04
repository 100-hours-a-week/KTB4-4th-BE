package kr.ktb.zura.needu.user.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import kr.ktb.zura.needu.user.dto.response.UserSummaryResponse;
import kr.ktb.zura.needu.user.entity.User;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByExternalId(Long externalId);

    List<User> findAllByExternalIdIn(Collection<Long> externalIds);

    // idx_users_birth_month_day_status 뒤에 붙는 PK 순서로 읽어 id 커서 조회가 인덱스 범위 안에서 끝난다
    @Query("""
            select new kr.ktb.zura.needu.user.dto.response.UserSummaryResponse(
                u.id, u.nickname, u.birthDate, u.tasteAnalysisCompleted)
            from User u
            where u.birthMonthDay in :birthMonthDays
              and u.status = kr.ktb.zura.needu.user.type.UserStatus.ACTIVE
              and u.id > :cursorUserId
            order by u.id asc
            """)
    List<UserSummaryResponse> findAllActiveByBirthMonthDaysAfterId(
            @Param("birthMonthDays") Collection<Integer> birthMonthDays,
            @Param("cursorUserId") Long cursorUserId,
            Limit limit
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :userId")
    Optional<User> findByIdForUpdate(@Param("userId") Long userId);
}
