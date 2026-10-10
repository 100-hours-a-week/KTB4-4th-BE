package kr.ktb.zura.needu.friend.service;

import kr.ktb.zura.needu.friend.dto.response.PokeResponse;
import kr.ktb.zura.needu.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PokeService {

    private final UserService userService;

    // TODO: 내 친구가 아니면 FRIEND_NOT_FOUND. 같은 친구에게 제한 시간(미결) 안에 다시 보내면
    //  FRIEND_POKE_TOO_FREQUENT와 남은 시간(retryAfterSeconds). pokes 테이블에 저장하고 POKE 알림을 보내도록 구현
    //  콕 찌르기 API
    @Transactional
    public PokeResponse createPoke(Long userId, Long friendUserId) {
        userService.validateActiveUser(userId);
        throw new UnsupportedOperationException("콕 찌르기 처리 로직 미구현");
    }
}
