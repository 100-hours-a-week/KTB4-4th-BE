package kr.ktb.zura.needu.aichat.service;

import java.util.List;
import java.util.UUID;

import kr.ktb.zura.needu.aichat.dto.response.AiMessageSummaryResponse;
import kr.ktb.zura.needu.aichat.entity.AiChatRoom;
import kr.ktb.zura.needu.aichat.entity.AiMessage;
import kr.ktb.zura.needu.aichat.repository.AiChatRoomRepository;
import kr.ktb.zura.needu.aichat.repository.AiMessageRepository;
import kr.ktb.zura.needu.aichat.type.SenderType;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.response.CursorPageResponse;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// clientMessageId 유일 제약과 사용자 메시지-AI 답변 연결이 핵심이라 실제 JPA(H2) 위에서 검증한다.
@DataJpaTest
@Import(AiMessageService.class)
class AiMessageServiceTest {

    private static final Long USER_ID = 1L;
    private static final UUID CLIENT_MESSAGE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String USER_CONTENT = "요즘 러닝에 관심이 생겼어.";
    private static final String AI_CONTENT = "러닝을 좋아하시는군요.";

    @Autowired
    private AiMessageService aiMessageService;

    @Autowired
    private AiChatRoomRepository aiChatRoomRepository;

    @Autowired
    private AiMessageRepository aiMessageRepository;

    @Autowired
    private EntityManager entityManager;

    private Long conversationId;

    @BeforeEach
    void setUp() {
        conversationId = aiChatRoomRepository.saveAndFlush(AiChatRoom.reserve(USER_ID)).getId();
    }

    @Test
    void newClientMessageId_createUserMessage_savesUserMessage() {
        AiMessage userMessage = aiMessageService.createUserMessage(conversationId, CLIENT_MESSAGE_ID, USER_CONTENT);

        assertThat(userMessage.getId()).isNotNull();
        assertThat(userMessage.getSenderType()).isEqualTo(SenderType.USER);
        assertThat(userMessage.getClientMessageId()).isEqualTo(CLIENT_MESSAGE_ID);
        assertThat(userMessage.getReplyToMessageId()).isNull();
    }

    @Test
    void duplicateClientMessageId_createUserMessage_throwsDataIntegrityViolation() {
        aiMessageService.createUserMessage(conversationId, CLIENT_MESSAGE_ID, USER_CONTENT);
        entityManager.clear();

        assertThatThrownBy(() -> aiMessageService.createUserMessage(conversationId, CLIENT_MESSAGE_ID, USER_CONTENT))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void savedUserMessage_findUserMessage_returnsIt() {
        AiMessage userMessage = aiMessageService.createUserMessage(conversationId, CLIENT_MESSAGE_ID, USER_CONTENT);
        entityManager.clear();

        assertThat(aiMessageService.findUserMessage(conversationId, CLIENT_MESSAGE_ID))
                .get()
                .extracting(AiMessage::getId)
                .isEqualTo(userMessage.getId());
    }

    @Test
    void unknownClientMessageId_findUserMessage_returnsEmpty() {
        assertThat(aiMessageService.findUserMessage(conversationId, UUID.randomUUID())).isEmpty();
    }

    @Test
    void userMessage_createReply_savesAiMessageLinkedToUserMessage() {
        AiMessage userMessage = aiMessageService.createUserMessage(conversationId, CLIENT_MESSAGE_ID, USER_CONTENT);

        AiMessage reply = aiMessageService.createReply(conversationId, userMessage.getId(), AI_CONTENT);
        aiMessageRepository.flush();
        entityManager.clear();

        assertThat(reply.getSenderType()).isEqualTo(SenderType.AI);
        assertThat(reply.getClientMessageId()).isNull();
        // 메시지 순서는 id 오름차순으로 판단한다
        assertThat(reply.getId()).isGreaterThan(userMessage.getId());
        assertThat(aiMessageService.findReply(userMessage.getId()))
                .get()
                .extracting(AiMessage::getContent)
                .isEqualTo(AI_CONTENT);
    }

    @Test
    void noReplyYet_findReply_returnsEmpty() {
        AiMessage userMessage = aiMessageService.createUserMessage(conversationId, CLIENT_MESSAGE_ID, USER_CONTENT);
        entityManager.clear();

        assertThat(aiMessageService.findReply(userMessage.getId())).isEmpty();
    }

    @Test
    void sameClientMessageIdInAnotherRoom_createUserMessage_savesBoth() {
        Long otherConversationId = aiChatRoomRepository.saveAndFlush(AiChatRoom.reserve(2L)).getId();
        aiMessageService.createUserMessage(conversationId, CLIENT_MESSAGE_ID, USER_CONTENT);

        aiMessageService.createUserMessage(otherConversationId, CLIENT_MESSAGE_ID, USER_CONTENT);

        assertThat(aiMessageRepository.findAll()).hasSize(2);
    }

    @Test
    void messagesSaved_findAllMessages_returnsOldestFirstWithoutNextCursor() {
        AiMessage first = saveUserMessage();
        AiMessage second = saveUserMessage();
        entityManager.clear();

        CursorPageResponse<AiMessageSummaryResponse> result =
                aiMessageService.findAllMessages(conversationId, null, 10);

        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextCursor()).isNull();
        assertThat(result.items()).extracting(AiMessageSummaryResponse::messageId)
                .containsExactly(first.getId(), second.getId());
    }

    @Test
    void moreMessagesThanSize_findAllMessages_returnsLatestPageWithNextCursor() {
        saveUserMessage();
        AiMessage second = saveUserMessage();
        AiMessage third = saveUserMessage();
        entityManager.clear();

        CursorPageResponse<AiMessageSummaryResponse> result =
                aiMessageService.findAllMessages(conversationId, null, 2);

        assertThat(result.hasNext()).isTrue();
        assertThat(result.items()).extracting(AiMessageSummaryResponse::messageId)
                .containsExactly(second.getId(), third.getId());
        // 다음 요청이 이어 받을 지점은 이번 페이지에서 가장 오래된 메시지다
        assertThat(AiMessageCursor.decode(result.nextCursor()).messageId()).isEqualTo(second.getId());
    }

    @Test
    void nextCursorGiven_findAllMessages_returnsOlderMessagesOnly() {
        AiMessage first = saveUserMessage();
        saveUserMessage();
        saveUserMessage();
        entityManager.clear();

        CursorPageResponse<AiMessageSummaryResponse> firstPage =
                aiMessageService.findAllMessages(conversationId, null, 2);
        CursorPageResponse<AiMessageSummaryResponse> secondPage =
                aiMessageService.findAllMessages(conversationId, firstPage.nextCursor(), 2);

        assertThat(secondPage.hasNext()).isFalse();
        assertThat(secondPage.nextCursor()).isNull();
        assertThat(secondPage.items()).extracting(AiMessageSummaryResponse::messageId)
                .containsExactly(first.getId());
    }

    @Test
    void noMessages_findAllMessages_returnsEmptyPage() {
        CursorPageResponse<AiMessageSummaryResponse> result =
                aiMessageService.findAllMessages(conversationId, null, 10);

        assertThat(result.items()).isEmpty();
        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextCursor()).isNull();
    }

    @Test
    void messagesFromBothSenders_findAllMessages_returnsRoleAndContent() {
        AiMessage userMessage = aiMessageService.createUserMessage(conversationId, CLIENT_MESSAGE_ID, USER_CONTENT);
        aiMessageService.createReply(conversationId, userMessage.getId(), AI_CONTENT);
        aiMessageRepository.flush();
        entityManager.clear();

        List<AiMessageSummaryResponse> items =
                aiMessageService.findAllMessages(conversationId, null, 10).items();

        assertThat(items).extracting(AiMessageSummaryResponse::role)
                .containsExactly(SenderType.USER, SenderType.AI);
        assertThat(items).extracting(AiMessageSummaryResponse::content)
                .containsExactly(USER_CONTENT, AI_CONTENT);
        assertThat(items).allSatisfy(item -> assertThat(item.createdAt()).isNotNull());
    }

    @Test
    void invalidCursor_findAllMessages_throwsInvalidRequestException() {
        assertThatThrownBy(() -> aiMessageService.findAllMessages(conversationId, "not-base64!!", 10))
                .isInstanceOf(BusinessException.class);
    }

    private AiMessage saveUserMessage() {
        return aiMessageService.createUserMessage(conversationId, UUID.randomUUID(), USER_CONTENT);
    }
}
