package kr.ktb.zura.needu.notification;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import kr.ktb.zura.needu.notification.entity.Notification;
import kr.ktb.zura.needu.notification.entity.NotificationEvent;
import kr.ktb.zura.needu.notification.repository.NotificationEventRepository;
import kr.ktb.zura.needu.notification.repository.NotificationRepository;
import kr.ktb.zura.needu.notification.type.NotificationResourceType;
import kr.ktb.zura.needu.notification.type.NotificationType;
import kr.ktb.zura.needu.user.entity.User;
import kr.ktb.zura.needu.user.repository.UserRepository;
import kr.ktb.zura.needu.user.type.Gender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest(properties = {
        "needu.rate-limit.policies.notification-summary.method=GET",
        "needu.rate-limit.policies.notification-summary.path-pattern=/api/v1/notifications/summary",
        "needu.rate-limit.policies.notification-summary.limit=" + NotificationSummaryIntegrationTest.RATE_LIMIT,
        "needu.rate-limit.policies.notification-summary.window=1m",
        "needu.rate-limit.policies.notification-summary.maximum-size=1000"
})
@AutoConfigureMockMvc
class NotificationSummaryIntegrationTest {

    static final int RATE_LIMIT = 2;

    private static final String URL = "/api/v1/notifications/summary";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationEventRepository notificationEventRepository;

    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void tearDown() {
        notificationRepository.deleteAll();
        notificationEventRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void unreadNotificationsExist_findNotificationSummary_returnsUnreadStatus() throws Exception {
        Long userId = saveUser(101L).getId();
        Notification unread = createNotification(userId, "summary-unread");
        Notification read = createNotification(userId, "summary-read");
        read.read();
        notificationRepository.saveAllAndFlush(List.of(unread, read));

        mockMvc.perform(get(URL).with(authenticatedUser(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("미읽음 알림 상태를 조회했습니다."))
                .andExpect(jsonPath("$.data.hasUnread").value(true))
                .andExpect(jsonPath("$.data.unreadCount").value(1));
    }

    @Test
    void unauthenticated_findNotificationSummary_returnsCommonUnauthorizedResponse() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("로그인이 필요합니다."))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void requestsOverLimit_findNotificationSummary_returnsTooManyRequests() throws Exception {
        Long userId = saveUser(102L).getId();
        for (int request = 0; request < RATE_LIMIT; request++) {
            mockMvc.perform(get(URL).with(authenticatedUser(userId)))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(get(URL).with(authenticatedUser(userId)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.message")
                        .value("요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."));
    }

    @Test
    void blockedUser_findNotificationSummary_returnsForbidden() throws Exception {
        User user = saveUser(103L);
        user.block();
        userRepository.saveAndFlush(user);

        mockMvc.perform(get(URL).with(authenticatedUser(user.getId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("이용이 제한된 계정입니다."))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void onboardingIncompleteUser_findNotificationSummary_returnsForbidden() throws Exception {
        User user = saveUser(104L);
        ReflectionTestUtils.setField(user, "onboardingCompleted", false);
        userRepository.saveAndFlush(user);

        mockMvc.perform(get(URL).with(authenticatedUser(user.getId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("온보딩 진행이 필요합니다."))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    private Notification createNotification(Long receiverUserId, String dedupKey) {
        NotificationEvent event = notificationEventRepository.save(NotificationEvent.create(
                NotificationType.POKE,
                10L,
                "알림 제목",
                "알림 내용",
                NotificationResourceType.USER,
                receiverUserId,
                dedupKey
        ));
        return Notification.create(event, receiverUserId, LocalDateTime.now().plusDays(1));
    }

    private User saveUser(Long externalId) {
        LocalDate birthDate = LocalDateTime.now().toLocalDate();
        User user = new User(externalId, "사용자", null, Gender.NONE, birthDate);
        user.completeOnboarding(Gender.NONE, birthDate);
        return userRepository.saveAndFlush(user);
    }

    private RequestPostProcessor authenticatedUser(Long userId) {
        return authentication(new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }
}
