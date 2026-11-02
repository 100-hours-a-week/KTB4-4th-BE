package kr.ktb.zura.needu.aichat.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import kr.ktb.zura.needu.aichat.entity.AiChatRoom;
import kr.ktb.zura.needu.aichat.entity.AiMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class AiMessageRepositoryTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final String USER_CONTENT = "요즘 러닝에 관심이 생겼어.";
    private static final String AI_CONTENT = "러닝을 좋아하시는군요.";

    @Autowired
    private AiMessageRepository aiMessageRepository;

    @Autowired
    private TestEntityManager entityManager;

    private AiChatRoom room;

    @BeforeEach
    void setUp() {
        room = entityManager.persistAndFlush(AiChatRoom.reserve(USER_ID));
    }

    @Test
    void messagesSaved_findAllByAiChatRoomId_returnsOnlyOwnRoomMessagesOrderedByIdDesc() {
        AiMessage first = saveUserMessage(room);
        AiMessage second = saveUserMessage(room);
        AiMessage third = saveUserMessage(room);
        saveUserMessage(entityManager.persistAndFlush(AiChatRoom.reserve(OTHER_USER_ID)));
        entityManager.clear();

        List<AiMessage> result = aiMessageRepository.findAllByAiChatRoomId(room.getId(), Limit.of(10));

        assertThat(result).extracting(AiMessage::getId)
                .containsExactly(third.getId(), second.getId(), first.getId());
    }

    @Test
    void deletedMessageExists_findAllByAiChatRoomId_excludesDeletedMessage() {
        AiMessage kept = saveUserMessage(room);
        AiMessage deleted = saveUserMessage(room);
        ReflectionTestUtils.setField(deleted, "deletedAt", LocalDateTime.now());
        entityManager.flush();
        entityManager.clear();

        List<AiMessage> result = aiMessageRepository.findAllByAiChatRoomId(room.getId(), Limit.of(10));

        assertThat(result).extracting(AiMessage::getId).containsExactly(kept.getId());
    }

    @Test
    void limitSmallerThanTotal_findAllByAiChatRoomId_returnsLatestMessagesOnly() {
        saveUserMessage(room);
        AiMessage second = saveUserMessage(room);
        AiMessage third = saveUserMessage(room);
        entityManager.clear();

        List<AiMessage> result = aiMessageRepository.findAllByAiChatRoomId(room.getId(), Limit.of(2));

        assertThat(result).extracting(AiMessage::getId).containsExactly(third.getId(), second.getId());
    }

    @Test
    void cursorGiven_findAllByAiChatRoomIdBeforeCursor_returnsOlderMessagesOnly() {
        AiMessage first = saveUserMessage(room);
        AiMessage second = saveUserMessage(room);
        AiMessage third = saveUserMessage(room);
        entityManager.clear();

        List<AiMessage> result =
                aiMessageRepository.findAllByAiChatRoomIdBeforeCursor(room.getId(), third.getId(), Limit.of(10));

        assertThat(result).extracting(AiMessage::getId).containsExactly(second.getId(), first.getId());
    }

    @Test
    void replyAlreadySaved_saveAndFlush_throwsDataIntegrityViolation() {
        AiMessage userMessage = saveUserMessage(room);
        aiMessageRepository.saveAndFlush(AiMessage.createReply(room, userMessage.getId(), AI_CONTENT, 5, false));

        assertThatThrownBy(() -> aiMessageRepository.saveAndFlush(
                AiMessage.createReply(room, userMessage.getId(), AI_CONTENT, 10, false)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void messagesWithoutReplyTo_saveAndFlush_savesAll() {
        saveUserMessage(room);
        saveUserMessage(room);
        aiMessageRepository.saveAndFlush(AiMessage.createGreeting(room, AI_CONTENT));
        entityManager.clear();

        List<AiMessage> result = aiMessageRepository.findAllByAiChatRoomId(room.getId(), Limit.of(10));

        assertThat(result).hasSize(3);
    }

    private AiMessage saveUserMessage(AiChatRoom aiChatRoom) {
        return entityManager.persistAndFlush(
                AiMessage.createUserMessage(aiChatRoom, UUID.randomUUID(), USER_CONTENT));
    }
}
