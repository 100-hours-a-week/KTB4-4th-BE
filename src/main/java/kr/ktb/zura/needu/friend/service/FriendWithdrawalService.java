package kr.ktb.zura.needu.friend.service;

import kr.ktb.zura.needu.friend.repository.FriendRepository;
import kr.ktb.zura.needu.friend.repository.FriendSyncRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FriendWithdrawalService {

    private final FriendRepository friendRepository;
    private final FriendSyncRequestRepository friendSyncRequestRepository;

    @Transactional
    public void deleteUserData(Long userId) {
        friendRepository.deleteAllRelatedToUserId(userId);
        friendSyncRequestRepository.deleteByUserId(userId);
    }
}
