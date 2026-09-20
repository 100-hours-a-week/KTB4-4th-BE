package kr.ktb.zura.needu.friend.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.friend.exception.FriendErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class KakaoFriendClient {

    private static final String FRIENDS_URI = "https://kapi.kakao.com/v1/api/talk/friends";
    private static final int PAGE_SIZE = 100;

    private final RestClient restClient;

    public KakaoFriendClient(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    public List<Long> findAllFriendIds(String accessToken) {
        List<Long> friendIds = new ArrayList<>();
        int offset = 0;
        try {
            while (true) {
                KakaoFriendsResponse response = restClient.get()
                        .uri(FRIENDS_URI + "?offset={offset}&limit={limit}", offset, PAGE_SIZE)
                        .headers(headers -> headers.setBearerAuth(accessToken))
                        .retrieve()
                        .body(KakaoFriendsResponse.class);
                if (response == null || response.totalCount() == null) {
                    throw new BusinessException(FriendErrorCode.FRIEND_KAKAO_SYNC_FAILED);
                }
                List<KakaoFriend> elements = response.elements() == null ? List.of() : response.elements();
                if (elements.stream().anyMatch(friend -> friend.id() == null)) {
                    throw new BusinessException(FriendErrorCode.FRIEND_KAKAO_SYNC_FAILED);
                }
                friendIds.addAll(elements.stream().map(KakaoFriend::id).toList());
                offset += elements.size();
                if (offset >= response.totalCount()) {
                    return List.copyOf(friendIds);
                }
                if (elements.isEmpty()) {
                    throw new BusinessException(FriendErrorCode.FRIEND_KAKAO_SYNC_FAILED);
                }
            }
        } catch (RestClientException exception) {
            throw new BusinessException(FriendErrorCode.FRIEND_KAKAO_SYNC_FAILED, null, exception);
        }
    }

    private record KakaoFriendsResponse(
            List<KakaoFriend> elements,
            @JsonProperty("total_count") Integer totalCount
    ) {
    }

    private record KakaoFriend(Long id) {
    }
}
