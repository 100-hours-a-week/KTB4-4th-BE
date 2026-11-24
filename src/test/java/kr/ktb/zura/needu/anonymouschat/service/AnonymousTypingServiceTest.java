package kr.ktb.zura.needu.anonymouschat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.LocalDateTime;
import kr.ktb.zura.needu.anonymouschat.entity.AnonymousRoom;
import kr.ktb.zura.needu.anonymouschat.exception.AnonymousChatErrorCode;
import kr.ktb.zura.needu.anonymouschat.ws.AnonymousMessageRedisEvent;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.exception.TooManyRequestsException;
import kr.ktb.zura.needu.common.ratelimit.EventRateLimiter;
import kr.ktb.zura.needu.user.exception.UserErrorCode;
import kr.ktb.zura.needu.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

class AnonymousTypingServiceTest {

    private static final Long ROOM_ID = 1L;
    private static final Long QUESTIONER_ID = 100L;
    private static final Long RECIPIENT_ID = 200L;
    private static final String TYPING_RATE_LIMIT_EVENT = "anonymous-chat-typing";

    private final AnonymousChatService anonymousChatService = mock(AnonymousChatService.class);
    private final UserService userService = mock(UserService.class);
    private final EventRateLimiter eventRateLimiter = mock(EventRateLimiter.class);
    private final AnonymousMessageEventPublisher publisher = mock(AnonymousMessageEventPublisher.class);
    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final AnonymousTypingService anonymousTypingService = new AnonymousTypingService(
            anonymousChatService, userService, eventRateLimiter, publisher, jsonMapper);

    @Test
    void questioner_updateTyping_publishesToRecipientWithoutSenderIdentity() {
        givenParticipatingRoom(QUESTIONER_ID, room());

        anonymousTypingService.updateTyping(QUESTIONER_ID, ROOM_ID, true);

        verify(eventRateLimiter).validateWithinLimit(TYPING_RATE_LIMIT_EVENT, QUESTIONER_ID);
        verify(userService).validateActiveUser(QUESTIONER_ID);
        AnonymousMessageRedisEvent event = capturePublishedEvent();
        assertThat(event.receiverUserId()).isEqualTo(RECIPIENT_ID);
        assertThat(event.event().toString())
                .isEqualTo("{\"type\":\"ANONYMOUS_CHAT_TYPING\",\"data\":{\"roomId\":1,\"isTyping\":true}}");
    }

    @Test
    void recipientStopsTyping_updateTyping_publishesFalseToQuestioner() {
        givenParticipatingRoom(RECIPIENT_ID, room());

        anonymousTypingService.updateTyping(RECIPIENT_ID, ROOM_ID, false);

        AnonymousMessageRedisEvent event = capturePublishedEvent();
        assertThat(event.receiverUserId()).isEqualTo(QUESTIONER_ID);
        assertThat(event.event().get("data").get("isTyping").asBoolean()).isFalse();
    }

    @Test
    void rateLimitExceeded_updateTyping_doesNotQueryRoomOrPublish() {
        willThrow(new TooManyRequestsException(3))
                .given(eventRateLimiter).validateWithinLimit(TYPING_RATE_LIMIT_EVENT, QUESTIONER_ID);

        assertThatThrownBy(() -> anonymousTypingService.updateTyping(QUESTIONER_ID, ROOM_ID, true))
                .isInstanceOf(TooManyRequestsException.class);

        verifyNoInteractions(userService, anonymousChatService, publisher);
    }

    @Test
    void inactiveUser_updateTyping_doesNotPublish() {
        willThrow(new BusinessException(UserErrorCode.USER_NOT_FOUND))
                .given(userService).validateActiveUser(QUESTIONER_ID);

        assertThatThrownBy(() -> anonymousTypingService.updateTyping(QUESTIONER_ID, ROOM_ID, true))
                .isInstanceOf(BusinessException.class);

        verifyNoInteractions(publisher);
    }

    @Test
    void nonParticipant_updateTyping_doesNotPublish() {
        given(anonymousChatService.findParticipatingRoom(QUESTIONER_ID, ROOM_ID))
                .willThrow(new BusinessException(AnonymousChatErrorCode.ANONYMOUS_CHAT_ROOM_FORBIDDEN));

        assertThatThrownBy(() -> anonymousTypingService.updateTyping(QUESTIONER_ID, ROOM_ID, true))
                .isInstanceOf(BusinessException.class);

        verifyNoInteractions(publisher);
    }

    @Test
    void partnerLeft_updateTyping_doesNotPublish() {
        AnonymousRoom room = room();
        room.leave(RECIPIENT_ID, LocalDateTime.of(2026, 11, 24, 1, 0));
        givenParticipatingRoom(QUESTIONER_ID, room);

        anonymousTypingService.updateTyping(QUESTIONER_ID, ROOM_ID, true);

        verifyNoInteractions(publisher);
    }

    @Test
    void publishFails_updateTyping_doesNotThrow() {
        givenParticipatingRoom(QUESTIONER_ID, room());
        willThrow(new IllegalStateException("redis down")).given(publisher).publish(any());

        assertThatCode(() -> anonymousTypingService.updateTyping(QUESTIONER_ID, ROOM_ID, true))
                .doesNotThrowAnyException();
    }

    private void givenParticipatingRoom(Long userId, AnonymousRoom room) {
        given(anonymousChatService.findParticipatingRoom(userId, ROOM_ID)).willReturn(room);
    }

    private AnonymousMessageRedisEvent capturePublishedEvent() {
        ArgumentCaptor<AnonymousMessageRedisEvent> captor = ArgumentCaptor.forClass(AnonymousMessageRedisEvent.class);
        verify(publisher).publish(captor.capture());
        return captor.getValue();
    }

    private static AnonymousRoom room() {
        return new AnonymousRoom(1L, QUESTIONER_ID, RECIPIENT_ID, "익명의 수달", "otter", "mint");
    }
}
