package kr.ktb.zura.needu.friend.service;

import java.util.Optional;

import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.friend.dto.response.FriendResponse;
import kr.ktb.zura.needu.friend.entity.Friend;
import kr.ktb.zura.needu.friend.exception.FriendErrorCode;
import kr.ktb.zura.needu.friend.repository.FriendRepository;
import kr.ktb.zura.needu.user.entity.User;
import kr.ktb.zura.needu.user.type.Gender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class FriendServiceTest {

    private static final Long OWNER_USER_ID = 1L;
    private static final Long FRIEND_USER_ID = 123L;

    @Mock
    private FriendRepository friendRepository;

    @InjectMocks
    private FriendService friendService;

    @Test
    void friendExists_findFriend_returnsFriendUser() {
        User owner = createUser(OWNER_USER_ID, "나");
        User friendUser = createUser(FRIEND_USER_ID, "친구");
        friendUser.completeTasteAnalysis();
        Friend friend = new Friend(owner, friendUser);
        given(friendRepository.findActiveFriend(OWNER_USER_ID, FRIEND_USER_ID)).willReturn(Optional.of(friend));

        FriendResponse response = friendService.findFriend(OWNER_USER_ID, FRIEND_USER_ID);

        assertThat(response).isEqualTo(new FriendResponse(FRIEND_USER_ID, "친구", null, true));
    }

    @Test
    void notFriend_findFriend_throwsFriendNotFound() {
        given(friendRepository.findActiveFriend(OWNER_USER_ID, FRIEND_USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> friendService.findFriend(OWNER_USER_ID, FRIEND_USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(FriendErrorCode.FRIEND_NOT_FOUND);
    }

    private User createUser(Long id, String nickname) {
        User user = new User(id, nickname, null, Gender.NONE, null);
        // ID는 DB에서 생성되므로 단위 테스트에서만 직접 설정한다.
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
