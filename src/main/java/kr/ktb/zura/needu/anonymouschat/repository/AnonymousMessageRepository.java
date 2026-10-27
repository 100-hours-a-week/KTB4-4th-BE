package kr.ktb.zura.needu.anonymouschat.repository;

import kr.ktb.zura.needu.anonymouschat.entity.AnonymousMessage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnonymousMessageRepository extends JpaRepository<AnonymousMessage, Long> {

    boolean existsByIdAndRoomId(Long id, Long roomId);
}
