package kr.ktb.zura.needu.aichat.service;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import kr.ktb.zura.needu.aichat.entity.AiChatRoom;
import kr.ktb.zura.needu.aichat.repository.AiChatRoomRepository;
import kr.ktb.zura.needu.aichat.repository.AiMessageRepository;
import kr.ktb.zura.needu.aichat.repository.AiReplyJobRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class AiChatWithdrawalServiceTest {

    private final AiChatRoomRepository roomRepository = mock(AiChatRoomRepository.class);
    private final AiMessageRepository messageRepository = mock(AiMessageRepository.class);
    private final AiReplyJobRepository replyJobRepository = mock(AiReplyJobRepository.class);
    private final AiChatWithdrawalService service =
            new AiChatWithdrawalService(roomRepository, messageRepository, replyJobRepository);

    @Test
    void roomsExist_deleteUserData_deletesReplyJobsAndMessagesBeforeRooms() {
        AiChatRoom first = mock(AiChatRoom.class);
        AiChatRoom second = mock(AiChatRoom.class);
        when(first.getId()).thenReturn(10L);
        when(second.getId()).thenReturn(20L);
        when(roomRepository.findAllByUserId(1L)).thenReturn(List.of(first, second));

        service.deleteUserData(1L);

        InOrder order = inOrder(replyJobRepository, messageRepository, roomRepository);
        order.verify(replyJobRepository).deleteAllByAiChatRoomIdIn(List.of(10L, 20L));
        order.verify(messageRepository).deleteAllByAiChatRoomIdIn(List.of(10L, 20L));
        order.verify(roomRepository).deleteAll(List.of(first, second));
    }

    @Test
    void roomsDoNotExist_deleteUserData_skipsDeletes() {
        when(roomRepository.findAllByUserId(1L)).thenReturn(List.of());

        service.deleteUserData(1L);

        verify(replyJobRepository, never()).deleteAllByAiChatRoomIdIn(org.mockito.ArgumentMatchers.anyList());
        verify(messageRepository, never()).deleteAllByAiChatRoomIdIn(org.mockito.ArgumentMatchers.anyList());
        verify(roomRepository, never()).deleteAll(org.mockito.ArgumentMatchers.anyList());
    }
}
