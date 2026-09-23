package kr.ktb.zura.needu.friend;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
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
        "needu.rate-limit.policies.friend-list.method=GET",
        "needu.rate-limit.policies.friend-list.path-pattern=/api/v1/friends",
        "needu.rate-limit.policies.friend-list.limit=" + FriendListIntegrationTest.RATE_LIMIT,
        "needu.rate-limit.policies.friend-list.window=1m",
        "needu.rate-limit.policies.friend-list.maximum-size=1000"
})
@AutoConfigureMockMvc
class FriendListIntegrationTest {

    static final int RATE_LIMIT = 2;

    private static final String URL = "/api/v1/friends";

    private final AtomicLong externalIdSequence = new AtomicLong();

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
    void friendsExist_findAllFriends_returnsRequestedPage() throws Exception {
        User owner = saveUser("사용자", LocalDate.of(2000, 1, 1));
        User first = saveUser("첫 번째", LocalDate.of(2000, 2, 29));
        User second = saveUser("두 번째", LocalDate.of(2000, 3, 1));
        friendRepository.saveAll(List.of(
                new Friend(owner.getId(), first.getId()),
                new Friend(owner.getId(), second.getId())
        ));

        mockMvc.perform(validRequest().with(authenticatedUser(owner.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("친구 목록 조회에 성공했습니다."))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].name").value("두 번째"))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.nextCursor").isString());

        mockMvc.perform(validRequest().param("sort", "birthday").with(authenticatedUser(owner.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].name").value("첫 번째"))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.nextCursor").isString());
    }

    @Test
    void unauthenticated_findAllFriends_returnsUnauthorized() throws Exception {
        mockMvc.perform(validRequest())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("로그인이 필요합니다."));
    }

    @Test
    void blockedUser_findAllFriends_returnsForbidden() throws Exception {
        User user = new User(nextExternalId(), "차단 사용자", null, Gender.NONE, LocalDate.of(2000, 1, 1));
        user.block();
        userRepository.save(user);

        mockMvc.perform(validRequest().with(authenticatedUser(user.getId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("이용이 제한된 계정입니다."));
    }

    @Test
    void missingUser_findAllFriends_returnsNotFound() throws Exception {
        mockMvc.perform(validRequest().with(authenticatedUser(Long.MAX_VALUE)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("사용자 정보를 찾을 수 없습니다."));
    }

    @Test
    void requestsOverLimit_findAllFriends_returnsTooManyRequests() throws Exception {
        User user = saveUser("사용자", LocalDate.of(2000, 1, 1));
        for (int i = 0; i < RATE_LIMIT; i++) {
            mockMvc.perform(validRequest().with(authenticatedUser(user.getId())))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(validRequest().with(authenticatedUser(user.getId())))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.message")
                        .value("요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."))
                .andExpect(jsonPath("$.data.retryAfterSeconds").isNumber());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder validRequest() {
        return get(URL).param("size", "1");
    }

    private User saveUser(String name, LocalDate birthDate) {
        return userRepository.save(new User(nextExternalId(), name, null, Gender.NONE, birthDate));
    }

    private long nextExternalId() {
        return externalIdSequence.incrementAndGet();
    }

    private RequestPostProcessor authenticatedUser(Long userId) {
        return authentication(new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }
}
