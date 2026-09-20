package kr.ktb.zura.needu.aichat.repository;

import kr.ktb.zura.needu.aichat.entity.AiMessage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiMessageRepository extends JpaRepository<AiMessage, Long> {
}
