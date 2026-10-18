package kr.ktb.zura.needu.friend.repository;

import java.time.LocalDateTime;
import java.util.List;
import kr.ktb.zura.needu.friend.entity.FriendSyncRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Limit;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class FriendSyncRequestRepositoryTest {

    @Autowired
    private FriendSyncRequestRepository friendSyncRequestRepository;

    @Test
    void dueRequests_findAllByNextAttemptAt_returnsOldestWithinLimit() {
        LocalDateTime currentTime = LocalDateTime.of(2026, 10, 14, 12, 0);
        friendSyncRequestRepository.saveAll(List.of(
                new FriendSyncRequest(4L, currentTime.minusMinutes(20)),
                new FriendSyncRequest(2L, currentTime.minusMinutes(10)),
                new FriendSyncRequest(1L, currentTime.minusMinutes(10)),
                new FriendSyncRequest(5L, currentTime.minusMinutes(5)),
                new FriendSyncRequest(3L, currentTime.plusMinutes(5))
        ));

        List<FriendSyncRequest> requests = friendSyncRequestRepository
                .findAllByNextAttemptAtLessThanEqualOrderByNextAttemptAtAscUserIdAsc(
                        currentTime, Limit.of(3));

        assertThat(requests)
                .extracting(FriendSyncRequest::getUserId)
                .containsExactly(4L, 1L, 2L);
    }
}
