package kr.ktb.zura.needu.aichat.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import kr.ktb.zura.needu.aichat.dto.response.DailyConversationLimitResponse;
import kr.ktb.zura.needu.aichat.entity.AiChatRoom;
import kr.ktb.zura.needu.aichat.entity.AiMessage;
import kr.ktb.zura.needu.aichat.exception.AiChatErrorCode;
import kr.ktb.zura.needu.aichat.repository.AiChatRoomRepository;
import kr.ktb.zura.needu.aichat.repository.AiMessageRepository;
import kr.ktb.zura.needu.aichat.type.AiChatRoomStatus;
import kr.ktb.zura.needu.aichat.type.SenderType;
import kr.ktb.zura.needu.common.config.ClockConfig;
import kr.ktb.zura.needu.common.exception.BusinessException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({AiChatRoomService.class, ClockConfig.class})
class AiChatRoomServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long UNKNOWN_ROOM_ID = 999L;
    private static final String GREETING = "안녕하세요";
    private static final LocalDateTime PURGE_AT = LocalDateTime.now(ZoneOffset.UTC).plusHours(1);
    private static final int DAILY_CONVERSATION_LIMIT = 6;
    private static final String DAILY_CONVERSATION_LIMIT_ZONE = "Asia/Seoul";
    // KST 2026-10-20 00:00 = UTC 2026-10-19 15:00
    private static final LocalDateTime KST_MIDNIGHT_IN_UTC = LocalDateTime.of(2026, 10, 19, 15, 0);

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

    @BeforeEach
    void setUp() {
        aiMessageRepository.deleteAll();
        aiChatRoomRepository.deleteAll();
    }

    @Test
    @DisplayName("기존에 대화방이 존재하지 않던 사용자가 대화방 진입 시 새로운 대화방을 생성한다.")
    void noRoom_findOrReserveRoom_reservesPendingRoom() {
        // Given: 대화방이 하나도 없던 사용자

        // When
        AiChatRoom room = aiChatRoomService.findOrReserveRoom(USER_ID);

        // Then
        assertThat(room.getId()).isNotNull();
        assertThat(room.getStatus()).isEqualTo(AiChatRoomStatus.PENDING);
        assertThat(room.getActiveUserId()).isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("활성화 되어 있던 기존 방이 존재하면 기존 활성화된 방을 돌려준다.")
    void activeRoomExists_findOrReserveRoom_returnsExistingRoom() {
        // Given
        AiChatRoom activeRoom = aiChatRoomService.findOrReserveRoom(USER_ID);
        aiChatRoomService.activateRoom(activeRoom.getId(), GREETING, PURGE_AT);

        // When
        AiChatRoom room = aiChatRoomService.findOrReserveRoom(USER_ID);

        // Then
        assertThat(room.getId()).isEqualTo(activeRoom.getId());
        assertThat(room.getStatus()).isEqualTo(AiChatRoomStatus.ACTIVE);
    }

    @Test
    @DisplayName("대화방을 준비하는 중에 다시 대화를 시작하면 거절된다.")
    void recentPendingRoomExists_findOrReserveRoom_throwsConversationStarting() {
        // Given
        aiChatRoomService.findOrReserveRoom(USER_ID);

        // When, Then
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
    void expiredRoom_reserveRoom_reservesNewPendingRoom() {
        AiChatRoom oldRoom = activateRoom(USER_ID);
        aiChatRoomService.expireRoom(oldRoom.getId());

        AiChatRoom newRoom = aiChatRoomService.reserveRoom(USER_ID);

        assertThat(newRoom.getId()).isNotEqualTo(oldRoom.getId());
        assertThat(newRoom.getStatus()).isEqualTo(AiChatRoomStatus.PENDING);
        assertThat(newRoom.getActiveUserId()).isEqualTo(USER_ID);
    }

    @Test
    void roomsStartedTodayReachLimit_findOrReserveRoom_throwsDailyConversationLimitExceeded() {
        startAndExpireRooms(DAILY_CONVERSATION_LIMIT);

        assertThatThrownBy(() -> aiChatRoomService.findOrReserveRoom(USER_ID))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(AiChatErrorCode.AICHAT_DAILY_CONVERSATION_LIMIT_EXCEEDED));
        assertThat(aiChatRoomRepository.findAllByUserId(USER_ID)).hasSize(DAILY_CONVERSATION_LIMIT);
    }

    @Test
    void roomsStartedTodayBelowLimit_findOrReserveRoom_reservesRoom() {
        startAndExpireRooms(DAILY_CONVERSATION_LIMIT - 1);

        AiChatRoom room = aiChatRoomService.findOrReserveRoom(USER_ID);

        assertThat(room.getStatus()).isEqualTo(AiChatRoomStatus.PENDING);
    }

    @Test
    void roomsOfAnotherUserReachLimit_findOrReserveRoom_reservesRoom() {
        for (int count = 0; count < DAILY_CONVERSATION_LIMIT; count++) {
            AiChatRoom room = activateRoom(OTHER_USER_ID);
            aiChatRoomService.expireRoom(room.getId());
        }

        AiChatRoom room = aiChatRoomService.findOrReserveRoom(USER_ID);

        assertThat(room.getStatus()).isEqualTo(AiChatRoomStatus.PENDING);
    }

    @Test
    void discardedRoomsReachLimit_findOrReserveRoom_reservesRoom() {
        for (int count = 0; count < DAILY_CONVERSATION_LIMIT; count++) {
            AiChatRoom reservedRoom = aiChatRoomService.findOrReserveRoom(USER_ID);
            aiChatRoomService.discardRoom(reservedRoom.getId());
        }

        AiChatRoom room = aiChatRoomService.findOrReserveRoom(USER_ID);

        assertThat(room.getStatus()).isEqualTo(AiChatRoomStatus.PENDING);
    }

    @Test
    void roomsStartedBeforeKstMidnight_findOrReserveRoom_reservesRoom() {
        startAndExpireRooms(DAILY_CONVERSATION_LIMIT);
        updateCreatedAt(USER_ID, KST_MIDNIGHT_IN_UTC.minusNanos(1_000));

        AiChatRoom room = aiChatRoomServiceAt(Instant.parse("2026-10-20T03:00:00Z")).findOrReserveRoom(USER_ID);

        assertThat(room.getStatus()).isEqualTo(AiChatRoomStatus.PENDING);
    }

    @Test
    void roomsStartedAtKstMidnight_findOrReserveRoom_throwsWithNextKstMidnight() {
        startAndExpireRooms(DAILY_CONVERSATION_LIMIT);
        updateCreatedAt(USER_ID, KST_MIDNIGHT_IN_UTC);
        AiChatRoomService serviceBeforeNextMidnight = aiChatRoomServiceAt(Instant.parse("2026-10-20T14:59:59Z"));

        assertThatThrownBy(() -> serviceBeforeNextMidnight.findOrReserveRoom(USER_ID))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode())
                            .isEqualTo(AiChatErrorCode.AICHAT_DAILY_CONVERSATION_LIMIT_EXCEEDED);
                    assertThat(exception.getData()).isEqualTo(new DailyConversationLimitResponse(
                            OffsetDateTime.parse("2026-10-20T15:00:00Z")));
                });
    }

    @Test
    void stalePendingRoomAndRoomsReachLimit_findOrReserveRoom_throwsDailyConversationLimitExceeded() {
        startAndExpireRooms(DAILY_CONVERSATION_LIMIT);
        AiChatRoom staleRoom = aiChatRoomService.reserveRoomWithoutDailyLimit(USER_ID);
        jdbcTemplate.update("update ai_chat_rooms set created_at = ? where id = ?",
                LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5), staleRoom.getId());
        entityManager.clear();

        assertThatThrownBy(() -> aiChatRoomService.findOrReserveRoom(USER_ID))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(AiChatErrorCode.AICHAT_DAILY_CONVERSATION_LIMIT_EXCEEDED));
    }

    @Test
    void roomsStartedTodayReachLimit_findOrReserveRoomWithoutDailyLimit_reservesRoom() {
        startAndExpireRooms(DAILY_CONVERSATION_LIMIT);

        AiChatRoom room = aiChatRoomService.findOrReserveRoomWithoutDailyLimit(USER_ID, Duration.ofMinutes(5));

        assertThat(room.getStatus()).isEqualTo(AiChatRoomStatus.PENDING);
    }

    @Test
    void roomsStartedTodayReachLimit_reserveRoom_throwsDailyConversationLimitExceeded() {
        startAndExpireRooms(DAILY_CONVERSATION_LIMIT);

        assertThatThrownBy(() -> aiChatRoomService.reserveRoom(USER_ID))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(AiChatErrorCode.AICHAT_DAILY_CONVERSATION_LIMIT_EXCEEDED));
    }

    @Test
    void roomsStartedTodayReachLimit_reserveRoomWithoutDailyLimit_reservesRoom() {
        startAndExpireRooms(DAILY_CONVERSATION_LIMIT);

        AiChatRoom room = aiChatRoomService.reserveRoomWithoutDailyLimit(USER_ID);

        assertThat(room.getStatus()).isEqualTo(AiChatRoomStatus.PENDING);
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
    void completedRoomOwnedByUser_validateReadableRoom_completes() {
        AiChatRoom completedRoom = activateRoom(USER_ID);
        aiChatRoomService.completeRoom(completedRoom.getId());

        assertThatCode(() -> aiChatRoomService.validateReadableRoom(USER_ID, completedRoom.getId()))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> aiChatRoomService.validateMessageSendableRoom(USER_ID, completedRoom.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(AiChatErrorCode.AICHAT_CONVERSATION_NOT_FOUND);
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
    void activeRoomOwnedByUser_findStreamableRoom_returnsRoom() {
        AiChatRoom activeRoom = activateRoom(USER_ID);

        AiChatRoom room = aiChatRoomService.findStreamableRoom(USER_ID, activeRoom.getId());

        assertThat(room.getId()).isEqualTo(activeRoom.getId());
        assertThat(room.getStatus()).isEqualTo(AiChatRoomStatus.ACTIVE);
    }

    @Test
    void analyzingRoom_findStreamableRoom_throwsInputLocked() {
        AiChatRoom activeRoom = activateRoom(USER_ID);
        aiChatRoomService.startAnalysis(activeRoom.getId());

        assertThatThrownBy(() -> aiChatRoomService.findStreamableRoom(USER_ID, activeRoom.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(AiChatErrorCode.AICHAT_INPUT_LOCKED);
    }

    @Test
    void roomOwnedByAnotherUser_findStreamableRoom_throwsConversationForbidden() {
        AiChatRoom activeRoom = activateRoom(OTHER_USER_ID);

        assertThatThrownBy(() -> aiChatRoomService.findStreamableRoom(USER_ID, activeRoom.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(AiChatErrorCode.AICHAT_CONVERSATION_FORBIDDEN);
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
        assertThat(completedRoom.getCompletedAt()).isNotNull();
        assertThat(completedRoom.getActiveUserId()).isNull();
    }

    @Test
    void recentlyCompletedRoom_findOrReserveRoom_returnsCompletedRoom() {
        AiChatRoom activeRoom = activateRoom(USER_ID);
        aiChatRoomService.completeRoom(activeRoom.getId());

        AiChatRoom room = aiChatRoomService.findOrReserveRoom(USER_ID);

        assertThat(room.getId()).isEqualTo(activeRoom.getId());
        assertThat(room.getStatus()).isEqualTo(AiChatRoomStatus.COMPLETED);
        assertThat(aiChatRoomService.findNextConversationAvailableAt(room))
                .isEqualTo(room.getCompletedAt().plusHours(24).atOffset(ZoneOffset.UTC));
    }

    @Test
    void recentlyCompletedRoomWithShortCooldown_findOrReserveRoom_returnsCompletedRoom() {
        AiChatRoom completedRoom = activateRoom(USER_ID);
        aiChatRoomService.completeRoom(completedRoom.getId());

        AiChatRoom room = aiChatRoomService.findOrReserveRoomWithoutDailyLimit(USER_ID, Duration.ofMinutes(5));

        assertThat(room.getId()).isEqualTo(completedRoom.getId());
        assertThat(aiChatRoomService.findNextConversationAvailableAt(room, Duration.ofMinutes(5)))
                .isEqualTo(room.getCompletedAt().plusMinutes(5).atOffset(ZoneOffset.UTC));
    }

    @Test
    void completedRoomAfterShortCooldown_findOrReserveRoom_reservesNewRoom() {
        AiChatRoom completedRoom = activateRoom(USER_ID);
        aiChatRoomService.completeRoom(completedRoom.getId());
        aiChatRoomRepository.flush();
        jdbcTemplate.update("update ai_chat_rooms set completed_at = ? where id = ?",
                LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5).minusSeconds(1), completedRoom.getId());
        entityManager.clear();

        AiChatRoom newRoom = aiChatRoomService.findOrReserveRoomWithoutDailyLimit(USER_ID, Duration.ofMinutes(5));

        assertThat(newRoom.getId()).isNotEqualTo(completedRoom.getId());
        assertThat(newRoom.getStatus()).isEqualTo(AiChatRoomStatus.PENDING);
    }

    @Test
    void completedRoomAfterCooldown_findOrReserveRoom_reservesNewRoom() {
        AiChatRoom activeRoom = activateRoom(USER_ID);
        aiChatRoomService.completeRoom(activeRoom.getId());
        aiChatRoomRepository.flush();
        jdbcTemplate.update("update ai_chat_rooms set completed_at = ? where id = ?",
                LocalDateTime.now(ZoneOffset.UTC).minusHours(24).minusSeconds(1), activeRoom.getId());
        entityManager.clear();

        AiChatRoom newRoom = aiChatRoomService.findOrReserveRoom(USER_ID);

        assertThat(newRoom.getId()).isNotEqualTo(activeRoom.getId());
        assertThat(newRoom.getStatus()).isEqualTo(AiChatRoomStatus.PENDING);
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

    private void startAndExpireRooms(int count) {
        for (int started = 0; started < count; started++) {
            AiChatRoom room = activateRoom(USER_ID);
            aiChatRoomService.expireRoom(room.getId());
        }
        aiChatRoomRepository.flush();
    }

    // created_at은 @CreationTimestamp로 채워지므로 특정 시각에 시작한 방을 만들기 위해 DB 값을 직접 바꾼다
    private void updateCreatedAt(Long userId, LocalDateTime createdAt) {
        jdbcTemplate.update("update ai_chat_rooms set created_at = ? where user_id = ?", createdAt, userId);
        entityManager.clear();
    }

    private AiChatRoomService aiChatRoomServiceAt(Instant now) {
        return new AiChatRoomService(aiChatRoomRepository, aiMessageRepository, Duration.ofMinutes(1),
                Duration.ofHours(24), DAILY_CONVERSATION_LIMIT, DAILY_CONVERSATION_LIMIT_ZONE,
                Clock.fixed(now, ZoneOffset.UTC));
    }
}
