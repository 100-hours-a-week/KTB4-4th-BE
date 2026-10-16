package kr.ktb.zura.needu.aichat.service;

import java.util.List;

import kr.ktb.zura.needu.aichat.entity.AiChatRoom;
import kr.ktb.zura.needu.aichat.repository.AiChatRoomRepository;
import kr.ktb.zura.needu.aichat.repository.AiMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AiChatWithdrawalService {

    private final AiChatRoomRepository aiChatRoomRepository;
    private final AiMessageRepository aiMessageRepository;

    @Transactional
    public void deleteUserData(Long userId) {
        List<AiChatRoom> rooms = aiChatRoomRepository.findAllByUserId(userId);
        if (rooms.isEmpty()) {
            return;
        }
        List<Long> roomIds = rooms.stream().map(AiChatRoom::getId).toList();
        aiMessageRepository.deleteAllByAiChatRoomIdIn(roomIds);
        aiChatRoomRepository.deleteAll(rooms);
    }

    @Transactional(readOnly = true)
    public List<Long> findAllRoomIds(Long userId) {
        return aiChatRoomRepository.findAllByUserId(userId).stream()
                .map(AiChatRoom::getId)
                .toList();
    }
}
