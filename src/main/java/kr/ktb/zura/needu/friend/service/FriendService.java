package kr.ktb.zura.needu.friend.service;

import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.friend.dto.response.FriendResponse;
import kr.ktb.zura.needu.friend.exception.FriendErrorCode;
import kr.ktb.zura.needu.friend.repository.FriendRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FriendService {

    private final FriendRepository friendRepository;

    // 내 친구 목록에 없는 사용자는 존재 여부를 드러내지 않도록 권한 없음이 아닌 친구 없음으로 응답한다.
    public FriendResponse findFriend(Long ownerUserId, Long friendUserId) {
        return friendRepository.findActiveFriend(ownerUserId, friendUserId)
                .map(FriendResponse::from)
                .orElseThrow(() -> new BusinessException(FriendErrorCode.FRIEND_NOT_FOUND));
    }
}
