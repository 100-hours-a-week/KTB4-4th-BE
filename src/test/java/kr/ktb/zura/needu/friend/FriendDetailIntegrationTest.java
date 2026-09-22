package kr.ktb.zura.needu.friend;

import java.time.LocalDate;
import java.util.List;
import kr.ktb.zura.needu.friend.entity.Friend;
import kr.ktb.zura.needu.friend.repository.FriendRepository;
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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "needu.rate-limit.policies.friend-detail.method=GET",
        "needu.rate-limit.policies.friend-detail.path-pattern=/api/v1/friends/{userId}",
        "needu.rate-limit.policies.friend-detail.limit=120",
        "needu.rate-limit.policies.friend-detail.window=1m",
        "needu.rate-limit.policies.friend-detail.maximum-size=100000"
})
@AutoConfigureMockMvc
class FriendDetailIntegrationTest {

    private static final String URL = "/api/v1/friends/{userId}";
    private static final int RATE_LIMIT = 120;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FriendRepository friendRepository;

    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void tearDown() {
        friendRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void friendExists_findFriend_returnsDetailsWithBirthDate() throws Exception {
        User owner = saveUser("사용자", null);
        User friend = saveUser("친구", LocalDate.of(2000, 2, 29));
        friendRepository.save(new Friend(owner.getId(), friend.getId()));

        mockMvc.perform(get(URL, friend.getId()).with(authenticatedUser(owner.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("친구 정보를 조회했습니다."))
                .andExpect(jsonPath("$.data.id").value(friend.getId()))
                .andExpect(jsonPath("$.data.nickname").value("친구"))
                .andExpect(jsonPath("$.data.profileImageUrl").isEmpty())
                .andExpect(jsonPath("$.data.tasteAnalysisCompleted").value(false))
                .andExpect(jsonPath("$.data.birthDate").value("2000-02-29"));
    }

    @Test
    void unauthenticated_findFriend_returnsUnauthorized() throws Exception {
        mockMvc.perform(get(URL, 1L))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void notFriend_findFriend_returnsNotFound() throws Exception {
        User owner = saveUser("사용자", null);
        User friend = saveUser("다른 사용자", null);
        friendRepository.save(new Friend(friend.getId(), owner.getId()));

        mockMvc.perform(get(URL, friend.getId()).with(authenticatedUser(owner.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("친구를 찾을 수 없습니다."));
    }

    @Test
    void requestsOverLimit_findFriend_returnsTooManyRequestsPerLoginUser() throws Exception {
        User owner = saveUser("사용자", null);
        User otherOwner = saveUser("다른 사용자", null);
        User friend = saveUser("친구", null);
        friendRepository.saveAll(List.of(
                new Friend(owner.getId(), friend.getId()),
                new Friend(otherOwner.getId(), friend.getId())));
        for (int i = 0; i < RATE_LIMIT; i++) {
            mockMvc.perform(get(URL, friend.getId()).with(authenticatedUser(owner.getId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.birthDate").isEmpty());
        }

        mockMvc.perform(get(URL, otherOwner.getId()).with(authenticatedUser(owner.getId())))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
        mockMvc.perform(get(URL, friend.getId()).with(authenticatedUser(otherOwner.getId())))
                .andExpect(status().isOk());
    }

    private User saveUser(String nickname, LocalDate birthDate) {
        return userRepository.save(new User(null, nickname, null, Gender.NONE, birthDate));
    }

    private RequestPostProcessor authenticatedUser(Long userId) {
        return authentication(new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }
}
