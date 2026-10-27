package kr.ktb.zura.needu.anonymouschat.service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import kr.ktb.zura.needu.anonymouschat.dto.request.CreateAnonymousReportRequest;
import kr.ktb.zura.needu.anonymouschat.dto.response.AnonymousReportResponse;
import kr.ktb.zura.needu.anonymouschat.entity.AnonymousReport;
import kr.ktb.zura.needu.anonymouschat.entity.AnonymousRoom;
import kr.ktb.zura.needu.anonymouschat.exception.AnonymousChatErrorCode;
import kr.ktb.zura.needu.anonymouschat.repository.AnonymousReportRepository;
import kr.ktb.zura.needu.anonymouschat.type.AnonymousReportReason;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.ErrorCode;
import kr.ktb.zura.needu.user.exception.UserErrorCode;
import kr.ktb.zura.needu.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AnonymousReportServiceTest {

    private static final Long ROOM_ID = 1L;
    private static final Long QUESTIONER_ID = 100L;
    private static final Long RECIPIENT_ID = 200L;
    private static final Long REPORT_ID = 31L;
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 10, 27, 1, 5);
    private static final CreateAnonymousReportRequest REQUEST =
            new CreateAnonymousReportRequest(AnonymousReportReason.ABUSE, "욕설을 보냈습니다.");

    @Mock
    private AnonymousChatService anonymousChatService;

    @Mock
    private AnonymousReportRepository anonymousReportRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private AnonymousReportService anonymousReportService;

    @Test
    void participant_reportRoom_savesReport() {
        AnonymousRoom room = givenParticipatingRoom(RECIPIENT_ID);
        given(anonymousReportRepository.saveAndFlush(any(AnonymousReport.class))).willAnswer(invocation -> {
            AnonymousReport report = invocation.getArgument(0);
            ReflectionTestUtils.setField(report, "id", REPORT_ID);
            ReflectionTestUtils.setField(report, "createdAt", CREATED_AT);
            return report;
        });

        AnonymousReportResponse response = anonymousReportService.reportRoom(RECIPIENT_ID, ROOM_ID, REQUEST);

        assertThat(response).isEqualTo(new AnonymousReportResponse(
                REPORT_ID, OffsetDateTime.of(CREATED_AT, ZoneOffset.UTC)));
        ArgumentCaptor<AnonymousReport> captor = ArgumentCaptor.forClass(AnonymousReport.class);
        verify(anonymousReportRepository).saveAndFlush(captor.capture());
        AnonymousReport saved = captor.getValue();
        assertThat(saved.getReporterUserId()).isEqualTo(RECIPIENT_ID);
        assertThat(saved.getReportedUserId()).isEqualTo(QUESTIONER_ID);
        assertThat(saved.getRoom()).isSameAs(room);
        assertThat(saved.getReason()).isEqualTo(AnonymousReportReason.ABUSE);
        assertThat(saved.getDescription()).isEqualTo("욕설을 보냈습니다.");
        verify(userService).validateActiveUser(RECIPIENT_ID);
    }

    @Test
    void alreadyReported_reportRoom_throwsDuplicated() {
        givenParticipatingRoom(RECIPIENT_ID);
        given(anonymousReportRepository.existsByReporterUserIdAndRoomId(RECIPIENT_ID, ROOM_ID)).willReturn(true);

        assertErrorCode(AnonymousChatErrorCode.ANONYMOUS_CHAT_REPORT_DUPLICATED);

        verify(anonymousReportRepository, never()).saveAndFlush(any());
    }

    @Test
    void concurrentDuplicate_reportRoom_throwsDuplicated() {
        givenParticipatingRoom(RECIPIENT_ID);
        given(anonymousReportRepository.saveAndFlush(any(AnonymousReport.class)))
                .willThrow(new DataIntegrityViolationException("uk_anonymous_reports_reporter_room"));

        assertErrorCode(AnonymousChatErrorCode.ANONYMOUS_CHAT_REPORT_DUPLICATED);
    }

    @Test
    void nonParticipant_reportRoom_throwsForbiddenWithoutSaving() {
        given(anonymousChatService.findParticipatingRoom(RECIPIENT_ID, ROOM_ID))
                .willThrow(new BusinessException(AnonymousChatErrorCode.ANONYMOUS_CHAT_ROOM_FORBIDDEN));

        assertErrorCode(AnonymousChatErrorCode.ANONYMOUS_CHAT_ROOM_FORBIDDEN);

        verifyNoInteractions(anonymousReportRepository);
    }

    @Test
    void inactiveUser_reportRoom_throwsWithoutQueryingRoom() {
        willThrow(new BusinessException(UserErrorCode.USER_BLOCKED))
                .given(userService).validateActiveUser(RECIPIENT_ID);

        assertErrorCode(UserErrorCode.USER_BLOCKED);

        verifyNoInteractions(anonymousChatService, anonymousReportRepository);
    }

    private void assertErrorCode(ErrorCode errorCode) {
        assertThatThrownBy(() -> anonymousReportService.reportRoom(RECIPIENT_ID, ROOM_ID, REQUEST))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(errorCode);
    }

    private AnonymousRoom givenParticipatingRoom(Long userId) {
        AnonymousRoom room = new AnonymousRoom(10L, QUESTIONER_ID, RECIPIENT_ID, "익명의 수달");
        ReflectionTestUtils.setField(room, "id", ROOM_ID);
        given(anonymousChatService.findParticipatingRoom(userId, ROOM_ID)).willReturn(room);
        return room;
    }
}
