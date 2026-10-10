package kr.ktb.zura.needu.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

@SpringBootTest(properties = {
        "needu.rate-limit.policies.notification-read.limit=" + NotificationReadIntegrationTest.RATE_LIMIT,
        "needu.rate-limit.policies.notification-read.maximum-size=1000"
})
@AutoConfigureMockMvc
class NotificationReadIntegrationTest {

    static final int RATE_LIMIT = 2;

    private static final String URL = "/api/v1/notifications/{notificationId}";

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
    void ownedUnreadNotification_readNotification_persistsReadAtAndReturnsRemainingCount() throws Exception {
        Long userId = saveUser(201L).getId();
        Notification notification = saveNotification(userId, 321L, "read-target");
        saveNotification(userId, 322L, "still-unread");

        mockMvc.perform(patch(URL, notification.getId()).with(authenticatedUser(userId)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("알림을 읽음 처리했습니다."))
                .andExpect(jsonPath("$.data.notificationId").value(notification.getId()))
                .andExpect(jsonPath("$.data.resourceType").value("USER"))
                .andExpect(jsonPath("$.data.resourceId").value(321))
                .andExpect(jsonPath("$.data.targetAvailable").value(true))
                .andExpect(jsonPath("$.data.unreadCount").value(1));

        assertThat(notificationRepository.findById(notification.getId()))
                .get()
                .extracting(Notification::getReadAt)
                .isNotNull();
    }

    @Test
    void requestsOverLimit_readNotification_returnsTooManyRequests() throws Exception {
        Long userId = saveUser(202L).getId();
        Notification notification = saveNotification(userId, 321L, "rate-limit");
        for (int request = 0; request < RATE_LIMIT; request++) {
            mockMvc.perform(patch(URL, notification.getId()).with(authenticatedUser(userId)).with(csrf()))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(patch(URL, notification.getId()).with(authenticatedUser(userId)).with(csrf()))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.message")
                        .value("요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."));
    }

    private Notification saveNotification(Long receiverUserId, Long resourceId, String dedupKey) {
        NotificationEvent event = notificationEventRepository.save(NotificationEvent.create(
                NotificationType.POKE,
                10L,
                "알림 제목",
                "알림 내용",
                NotificationResourceType.USER,
                resourceId,
                dedupKey
        ));
        return notificationRepository.saveAndFlush(
                Notification.create(event, receiverUserId, LocalDateTime.now().plusDays(1)));
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
