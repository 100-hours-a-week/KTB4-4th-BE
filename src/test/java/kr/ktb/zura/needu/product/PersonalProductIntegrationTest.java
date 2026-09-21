package kr.ktb.zura.needu.product;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import kr.ktb.zura.needu.product.entity.PersonalProduct;
import kr.ktb.zura.needu.product.entity.Product;
import kr.ktb.zura.needu.product.repository.PersonalProductRepository;
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
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 테스트용 application.properties가 운영 설정 파일을 가리므로, 요청 제한 정책은 테스트에서 직접 지정한다.
@SpringBootTest(properties = {
        "needu.rate-limit.policies.personal-recommendations.method=GET",
        "needu.rate-limit.policies.personal-recommendations.path-pattern=/api/v1/users/me/personal-recommendations",
        "needu.rate-limit.policies.personal-recommendations.limit=" + PersonalProductIntegrationTest.RATE_LIMIT,
        "needu.rate-limit.policies.personal-recommendations.window=1m",
        "needu.rate-limit.policies.personal-recommendations.maximum-size=1000"
})
@AutoConfigureMockMvc
class PersonalProductIntegrationTest {

    static final int RATE_LIMIT = 10;

    private static final String URL = "/api/v1/users/me/personal-recommendations";

    private final AtomicLong externalIdSequence = new AtomicLong();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private PersonalProductRepository personalProductRepository;

    @AfterEach
    void tearDown() {
        personalProductRepository.deleteAll();
        // product 도메인에 아직 ProductRepository가 없어 테스트 데이터 정리에만 EntityManager를 사용한다.
        transactionTemplate.executeWithoutResult(status ->
                entityManager.createQuery("delete from Product").executeUpdate());
        userRepository.deleteAll();
    }

    @Test
    void personalProductsExist_findAllPersonalProducts_paginatesWithCursorUntilLastPage() throws Exception {
        User user = userRepository.save(createUser());
        User otherUser = userRepository.save(createUser());
        PersonalProduct lamp = savePersonalProduct(user.getId(), "0.900000", "미니멀 테이블 램프", "52000.00");
        PersonalProduct chair = savePersonalProduct(user.getId(), "0.800000", "원목 의자", "89000.00");
        PersonalProduct mug = savePersonalProduct(user.getId(), "0.700000", "머그컵", "15000.00");
        savePersonalProduct(otherUser.getId(), "0.990000", "다른 사용자 상품", "1000.00");

        String firstPage = mockMvc.perform(get(URL).param("size", "2").with(authenticatedUser(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("개인 추천 상품 목록을 조회했습니다."))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].recommendationId").value(lamp.getId()))
                .andExpect(jsonPath("$.data.items[0].productId").value(lamp.getProduct().getId()))
                .andExpect(jsonPath("$.data.items[0].name").value("미니멀 테이블 램프"))
                .andExpect(jsonPath("$.data.items[0].imageUrl").value("https://image.test/product.png"))
                .andExpect(jsonPath("$.data.items[0].price").value(52000))
                .andExpect(jsonPath("$.data.items[1].recommendationId").value(chair.getId()))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andReturn().getResponse().getContentAsString();
        String nextCursor = jsonMapper.readTree(firstPage).get("nextCursor").asString();

        mockMvc.perform(get(URL).param("cursor", nextCursor).param("size", "2").with(authenticatedUser(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].recommendationId").value(mug.getId()))
                .andExpect(jsonPath("$.nextCursor").isEmpty())
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void noPersonalProducts_findAllPersonalProducts_returnsEmptyItems() throws Exception {
        User user = userRepository.save(createUser());

        mockMvc.perform(get(URL).with(authenticatedUser(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.nextCursor").isEmpty())
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void invalidCursor_findAllPersonalProducts_returnsBadRequest() throws Exception {
        User user = userRepository.save(createUser());

        mockMvc.perform(get(URL).param("cursor", "invalid!!").with(authenticatedUser(user.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("요청 형식이 올바르지 않습니다."))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void sizeOutOfRange_findAllPersonalProducts_returnsUnprocessableContent() throws Exception {
        User user = userRepository.save(createUser());

        mockMvc.perform(get(URL).param("size", "51").with(authenticatedUser(user.getId())))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("입력값이 유효하지 않습니다. 입력 내용을 확인해 주세요."))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void blockedUser_findAllPersonalProducts_returnsForbidden() throws Exception {
        User user = createUser();
        user.block();
        userRepository.save(user);

        mockMvc.perform(get(URL).with(authenticatedUser(user.getId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("이용이 제한된 계정입니다."));
    }

    @Test
    void requestsOverLimitPerMinute_findAllPersonalProducts_returnsTooManyRequests() throws Exception {
        User user = userRepository.save(createUser());
        User otherUser = userRepository.save(createUser());

        for (int i = 0; i < RATE_LIMIT; i++) {
            mockMvc.perform(get(URL).with(authenticatedUser(user.getId())))
                    .andExpect(status().isOk());
        }

        String response = mockMvc.perform(get(URL).with(authenticatedUser(user.getId())))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.message").value("요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."))
                .andReturn().getResponse().getContentAsString();
        JsonNode retryAfterSeconds = jsonMapper.readTree(response).get("data").get("retryAfterSeconds");
        assertThat(retryAfterSeconds.asLong()).isBetween(1L, 60L);

        mockMvc.perform(get(URL).with(authenticatedUser(otherUser.getId())))
                .andExpect(status().isOk());
    }

    private User createUser() {
        return new User(externalIdSequence.incrementAndGet(), "니듀", null, Gender.FEMALE, LocalDate.of(2000, 1, 1));
    }

    private PersonalProduct savePersonalProduct(Long userId, String score, String name, String price) {
        Product product = new Product(
                null, name, null, null, new BigDecimal(price), "https://image.test/product.png", null);
        transactionTemplate.executeWithoutResult(status -> entityManager.persist(product));
        return personalProductRepository.save(new PersonalProduct(userId, product, new BigDecimal(score), null));
    }

    private RequestPostProcessor authenticatedUser(Long userId) {
        return authentication(new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }
}
