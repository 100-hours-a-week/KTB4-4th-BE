package kr.ktb.zura.needu.friend.repository;

import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import kr.ktb.zura.needu.friend.entity.FriendSyncRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import static org.assertj.core.api.Assertions.assertThat;

// insert ignore는 MySQL 문법이라 내장 H2로 교체하지 않고 integration 프로필의 MySQL을 사용한다.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class FriendSyncRequestRepositoryIntegrationTest {

    @Autowired
    private FriendSyncRequestRepository friendSyncRequestRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void existingRequest_createIfAbsent_keepsRetryState() {
        LocalDateTime requestedAt = LocalDateTime.of(2026, 10, 14, 12, 0);
        FriendSyncRequest request = new FriendSyncRequest(1L, requestedAt);
        request.retryAt(requestedAt.plusMinutes(5));
        friendSyncRequestRepository.saveAndFlush(request);

        int insertedCount = friendSyncRequestRepository.createIfAbsent(1L, requestedAt.plusMinutes(1));
        friendSyncRequestRepository.flush();
        entityManager.clear();

        FriendSyncRequest savedRequest = friendSyncRequestRepository.findById(1L).orElseThrow();
        assertThat(insertedCount).isZero();
        assertThat(savedRequest.getRequestedAt()).isEqualTo(requestedAt);
        assertThat(savedRequest.getNextAttemptAt()).isEqualTo(requestedAt.plusMinutes(5));
        assertThat(savedRequest.getRetryCount()).isEqualTo(1);
    }

    @Test
    void missingRequest_createIfAbsent_insertsRequest() {
        LocalDateTime requestedAt = LocalDateTime.of(2026, 10, 14, 12, 0);

        int insertedCount = friendSyncRequestRepository.createIfAbsent(1L, requestedAt);
        friendSyncRequestRepository.flush();

        FriendSyncRequest savedRequest = friendSyncRequestRepository.findById(1L).orElseThrow();
        assertThat(insertedCount).isEqualTo(1);
        assertThat(savedRequest.getRequestedAt()).isEqualTo(requestedAt);
        assertThat(savedRequest.getNextAttemptAt()).isEqualTo(requestedAt);
        assertThat(savedRequest.getRetryCount()).isZero();
    }
}
