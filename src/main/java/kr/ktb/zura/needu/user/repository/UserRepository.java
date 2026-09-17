package kr.ktb.zura.needu.user.repository;

import java.util.Optional;
import kr.ktb.zura.needu.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByExternalId(Long externalId);
}
