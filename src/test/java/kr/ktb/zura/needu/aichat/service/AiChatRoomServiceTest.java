package kr.ktb.zura.needu.aichat.service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import kr.ktb.zura.needu.aichat.entity.AiChatRoom;
import kr.ktb.zura.needu.aichat.entity.AiMessage;
import kr.ktb.zura.needu.aichat.exception.AiChatErrorCode;
import kr.ktb.zura.needu.aichat.repository.AiChatRoomRepository;
import kr.ktb.zura.needu.aichat.repository.AiMessageRepository;
import kr.ktb.zura.needu.aichat.type.AiChatRoomStatus;
import kr.ktb.zura.needu.aichat.type.SenderType;
import kr.ktb.zura.needu.common.exception.BusinessException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 유일 제약과 flush 순서가 핵심이라 실제 JPA(H2) 위에서 검증한다.
@DataJpaTest
@Import(AiChatRoomService.class)
class AiChatRoomServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long UNKNOWN_ROOM_ID = 999L;
    private static final String GREETING = "안녕하세요";
    private static final LocalDateTime PURGE_AT = LocalDateTime.now(ZoneOffset.UTC).plusHours(1);

    @Autowired
    private AiChatRoomService aiChatRoomService;

    @Autowired
    private AiChatRoomRepository aiChatRoomRepository;

    @Autowired
    private AiMessageRepository aiMessageRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Test
    void noRoom_findOrReserveRoom_reservesPendingRoom() {
        AiChatRoom room = aiChatRoomService.findOrReserveRoom(USER_ID);

        assertThat(room.getId()).isNotNull();
        assertThat(room.getStatus()).isEqualTo(AiChatRoomStatus.PENDING);
        assertThat(room.getActiveUserId()).isEqualTo(USER_ID);
    }

    @Test
    void activeRoomExists_findOrReserveRoom_returnsExistingRoom() {
        AiChatRoom activeRoom = aiChatRoomService.findOrReserveRoom(USER_ID);
        aiChatRoomService.activateRoom(activeRoom.getId(), GREETING, PURGE_AT);

        AiChatRoom room = aiChatRoomService.findOrReserveRoom(USER_ID);

        assertThat(room.getId()).isEqualTo(activeRoom.getId());
        assertThat(room.getStatus()).isEqualTo(AiChatRoomStatus.ACTIVE);
    }

    @Test
    void recentPendingRoomExists_findOrReserveRoom_throwsConversationStarting() {
        aiChatRoomService.findOrReserveRoom(USER_ID);

        assertThatThrownBy(() -> aiChatRoomService.findOrReserveRoom(USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(AiChatErrorCode.AICHAT_CONVERSATION_STARTING);
    }

    @Test
    void stalePendingRoomExists_findOrReserveRoom_discardsItAndReservesNewRoom() {
        AiChatRoom staleRoom = aiChatRoomService.findOrReserveRoom(USER_ID);
        // created_at은 @CreationTimestamp로 채워지므로 오래된 예약을 만들기 위해 DB 값을 직접 바꾼다.
        jdbcTemplate.update("update ai_chat_rooms set created_at = ? where id = ?",
                LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5), staleRoom.getId());
        entityManager.clear();

        AiChatRoom room = aiChatRoomService.findOrReserveRoom(USER_ID);

        assertThat(room.getId()).isNotEqualTo(staleRoom.getId());
        AiChatRoom discardedRoom = aiChatRoomRepository.findById(staleRoom.getId()).orElseThrow();
        assertThat(discardedRoom.getDeletedAt()).isNotNull();
        assertThat(discardedRoom.getActiveUserId()).isNull();
    }

    @Test
    void activeRoom_expireAndReserveRoom_expiresOldRoomAndReservesNewRoom() {
        AiChatRoom oldRoom = aiChatRoomService.findOrReserveRoom(USER_ID);
        aiChatRoomService.activateRoom(oldRoom.getId(), GREETING, PURGE_AT);

        AiChatRoom newRoom = aiChatRoomService.expireAndReserveRoom(USER_ID, oldRoom.getId());

        assertThat(newRoom.getStatus()).isEqualTo(AiChatRoomStatus.PENDING);
        AiChatRoom expiredRoom = aiChatRoomRepository.findById(oldRoom.getId()).orElseThrow();
        assertThat(expiredRoom.getStatus()).isEqualTo(AiChatRoomStatus.EXPIRED);
        assertThat(expiredRoom.getActiveUserId()).isNull();
    }

    @Test
    void reservedRoom_activateRoom_setsExpirationAtAndSavesGreeting() {
        AiChatRoom reservedRoom = aiChatRoomService.findOrReserveRoom(USER_ID);

        AiChatRoom room = aiChatRoomService.activateRoom(reservedRoom.getId(), GREETING, PURGE_AT);

        assertThat(room.getStatus()).isEqualTo(AiChatRoomStatus.ACTIVE);
        assertThat(room.getPurgeAt()).isEqualTo(PURGE_AT);
        List<AiMessage> messages = aiMessageRepository.findAll();
        assertThat(messages).singleElement().satisfies(message -> {
            assertThat(message.getSenderType()).isEqualTo(SenderType.AI);
            assertThat(message.getContent()).isEqualTo("안녕하세요");
        });
    }

    @Test
    void activeRoomOwnedByUser_validateActiveRoom_completes() {
        AiChatRoom activeRoom = activateRoom(USER_ID);

        assertThatCode(() -> aiChatRoomService.validateActiveRoom(USER_ID, activeRoom.getId()))
                .doesNotThrowAnyException();
    }

    @Test
    void unknownRoomId_validateActiveRoom_throwsConversationNotFound() {
        assertThatThrownBy(() -> aiChatRoomService.validateActiveRoom(USER_ID, UNKNOWN_ROOM_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(AiChatErrorCode.AICHAT_CONVERSATION_NOT_FOUND);
    }

    @Test
    void roomOwnedByAnotherUser_validateActiveRoom_throwsConversationForbidden() {
        AiChatRoom activeRoom = activateRoom(OTHER_USER_ID);

        assertThatThrownBy(() -> aiChatRoomService.validateActiveRoom(USER_ID, activeRoom.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(AiChatErrorCode.AICHAT_CONVERSATION_FORBIDDEN);
    }

    @Test
    void pendingRoom_validateActiveRoom_throwsConversationNotFound() {
        AiChatRoom pendingRoom = aiChatRoomService.findOrReserveRoom(USER_ID);

        assertThatThrownBy(() -> aiChatRoomService.validateActiveRoom(USER_ID, pendingRoom.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(AiChatErrorCode.AICHAT_CONVERSATION_NOT_FOUND);
    }

    @Test
    void purgeAtPassed_validateActiveRoom_throwsConversationNotFound() {
        AiChatRoom activeRoom = activateRoom(USER_ID);
        jdbcTemplate.update("update ai_chat_rooms set purge_at = ? where id = ?",
                LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1), activeRoom.getId());
        entityManager.clear();

        assertThatThrownBy(() -> aiChatRoomService.validateActiveRoom(USER_ID, activeRoom.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(AiChatErrorCode.AICHAT_CONVERSATION_NOT_FOUND);
    }

    @Test
    void activeRoom_expireRoom_marksExpiredAndReleasesActiveUser() {
        AiChatRoom activeRoom = activateRoom(USER_ID);

        aiChatRoomService.expireRoom(activeRoom.getId());
        aiChatRoomRepository.flush();
        entityManager.clear();

        AiChatRoom expiredRoom = aiChatRoomRepository.findById(activeRoom.getId()).orElseThrow();
        assertThat(expiredRoom.getStatus()).isEqualTo(AiChatRoomStatus.EXPIRED);
        assertThat(expiredRoom.getActiveUserId()).isNull();
    }

    @Test
    void inputUnlockedRoom_validateAnalyzableRoom_throwsAnalysisNotReady() {
        AiChatRoom activeRoom = activateRoom(USER_ID);

        assertThatThrownBy(() -> aiChatRoomService.validateAnalyzableRoom(USER_ID, activeRoom.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(AiChatErrorCode.AICHAT_ANALYSIS_NOT_READY);

        aiChatRoomService.startAnalysis(activeRoom.getId());

        assertThatThrownBy(() -> aiChatRoomService.validateAnalyzableRoom(USER_ID, activeRoom.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(AiChatErrorCode.AICHAT_ANALYSIS_NOT_READY);
    }

    @Test
    void activeRoom_completeRoom_marksCompletedAndReleasesActiveUser() {
        AiChatRoom activeRoom = activateRoom(USER_ID);

        aiChatRoomService.completeRoom(activeRoom.getId());
        aiChatRoomRepository.flush();
        entityManager.clear();

        AiChatRoom completedRoom = aiChatRoomRepository.findById(activeRoom.getId()).orElseThrow();
        assertThat(completedRoom.getStatus()).isEqualTo(AiChatRoomStatus.COMPLETED);
        assertThat(completedRoom.getActiveUserId()).isNull();
    }

    @Test
    void activeRoom_startAnalysis_marksAnalyzing() {
        AiChatRoom activeRoom = activateRoom(USER_ID);

        aiChatRoomService.startAnalysis(activeRoom.getId());
        aiChatRoomRepository.flush();
        entityManager.clear();

        AiChatRoom analyzingRoom = aiChatRoomRepository.findById(activeRoom.getId()).orElseThrow();
        assertThat(analyzingRoom.getStatus()).isEqualTo(AiChatRoomStatus.ANALYZING);
        assertThat(analyzingRoom.getActiveUserId()).isEqualTo(USER_ID);
    }

    @Test
    void pendingRoom_discardRoom_releasesActiveUserSoNewRoomCanBeReserved() {
        AiChatRoom reservedRoom = aiChatRoomService.findOrReserveRoom(USER_ID);

        aiChatRoomService.discardRoom(reservedRoom.getId());
        aiChatRoomRepository.flush();

        assertThat(aiChatRoomService.findOrReserveRoom(USER_ID).getId()).isNotEqualTo(reservedRoom.getId());
    }

    private AiChatRoom activateRoom(Long userId) {
        AiChatRoom reservedRoom = aiChatRoomService.findOrReserveRoom(userId);
        return aiChatRoomService.activateRoom(reservedRoom.getId(), GREETING, PURGE_AT);
    }
}
