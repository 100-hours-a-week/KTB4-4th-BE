package kr.ktb.zura.needu.aichat.repository;

import java.util.List;
import java.util.Optional;

import kr.ktb.zura.needu.aichat.entity.AiChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiChatRoomRepository extends JpaRepository<AiChatRoom, Long> {

    List<AiChatRoom> findAllByUserId(Long userId);

    Optional<AiChatRoom> findByActiveUserId(Long activeUserId);

    Optional<AiChatRoom> findFirstByUserIdAndCompletedAtIsNotNullOrderByCompletedAtDesc(Long userId);
}
