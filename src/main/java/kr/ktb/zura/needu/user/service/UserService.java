package kr.ktb.zura.needu.user.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.user.dto.response.AiSummaryResponse;
import kr.ktb.zura.needu.user.dto.response.MyPageResponse;
import kr.ktb.zura.needu.user.dto.response.MyProfileResponse;
import kr.ktb.zura.needu.user.dto.response.TasteProfileResponse;
import kr.ktb.zura.needu.user.dto.response.UserDetailResponse;
import kr.ktb.zura.needu.user.dto.response.UserResponse;
import kr.ktb.zura.needu.user.dto.response.UserSummaryResponse;
import kr.ktb.zura.needu.user.entity.User;
import kr.ktb.zura.needu.user.entity.UserTasteProfile;
import kr.ktb.zura.needu.user.exception.UserErrorCode;
import kr.ktb.zura.needu.user.repository.UserRepository;
import kr.ktb.zura.needu.user.repository.UserTasteProfileRepository;
import kr.ktb.zura.needu.user.type.Gender;
import kr.ktb.zura.needu.user.type.TasteProfileStatus;
import kr.ktb.zura.needu.user.type.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private static final String DEFAULT_NICKNAME = "사용자";
    private static final int MAX_NICKNAME_LENGTH = 50;

    private final UserRepository userRepository;
    private final UserTasteProfileRepository userTasteProfileRepository;
    private final JsonMapper jsonMapper;

    @Transactional
    public UserResponse findOrCreateKakaoUser(Long kakaoId, String nickname, String profileImageUrl) {
        User user = userRepository.findByExternalId(kakaoId)
                .orElseGet(() -> userRepository.save(new User(
                        kakaoId, normalizeNickname(nickname), profileImageUrl, Gender.NONE, null)));
        validateLoginAvailableUser(user);
        user.recordLogin();

        return UserResponse.from(user);
    }

    public void validateAuthenticatableUser(Long userId) {
        validateLoginAvailableUser(findUser(userId));
    }

    public UserResponse findAuthenticatedUser(Long userId) {
        User user = findUser(userId);
        validateLoginAvailableUser(user);
        return UserResponse.from(user);
    }

    public void validateActiveUser(Long userId) {
        validateActiveUser(findUser(userId));
    }

    public UserSummaryResponse findUserSummary(Long userId) {
        User user = findUser(userId);
        validateActiveUser(user);
        return UserSummaryResponse.from(user);
    }

    public MyPageResponse findMyPage(Long userId) {
        User user = findUser(userId);
        validateActiveUser(user);
        return new MyPageResponse(MyProfileResponse.from(user), findTasteProfile(user));
    }

    public TasteProfileResponse findTasteProfile(Long userId) {
        return findTasteProfile(findUser(userId));
    }

    public boolean isOnboardingCompleted(Long userId) {
        User user = findUser(userId);
        validateLoginAvailableUser(user);
        return user.isOnboardingCompleted();
    }

    public Optional<UserDetailResponse> findUserById(Long userId) {
        return userRepository.findById(userId)
                .filter(User::isActive)
                .map(UserDetailResponse::from);
    }

    public Map<Long, Long> findUserIdsByExternalIds(Collection<Long> externalIds) {
        return userRepository.findAllByExternalIdIn(externalIds).stream()
                .collect(Collectors.toMap(User::getExternalId, User::getId));
    }

    public boolean isKakaoFriendSynced(Long userId) {
        return findUser(userId).isKakaoFriendSynced();
    }

    public void validateKakaoIdentity(Long userId, Long kakaoUserId) {
        if (!findUser(userId).hasExternalId(kakaoUserId)) {
            throw new BusinessException(UserErrorCode.USER_KAKAO_ACCOUNT_MISMATCH);
        }
    }

    @Transactional
    public void completeKakaoFriendSync(Long userId) {
        findUser(userId).completeKakaoFriendSync();
    }

    @Transactional
    public void completeTasteAnalysis(
            Long userId, String summary, List<String> tastes, List<String> interests) {
        User user = findUser(userId);
        UserTasteProfile profile = userTasteProfileRepository.findById(userId)
                .orElseGet(() -> new UserTasteProfile(user, Map.of()));
        profile.updateAnalysis(summary, toJson(tastes), toJson(interests));
        userTasteProfileRepository.save(profile);
        user.completeTasteAnalysis();
    }

    private TasteProfileResponse findTasteProfile(User user) {
        if (!user.isTasteAnalysisCompleted()) {
            return TasteProfileResponse.notStarted();
        }
        return userTasteProfileRepository.findById(user.getId())
                .map(profile -> new TasteProfileResponse(
                        TasteProfileStatus.COMPLETED,
                        toKeywords(profile.getRecentTastes()),
                        toKeywords(profile.getRecentInterests()),
                        AiSummaryResponse.from(profile.getAiSummary())))
                .orElseGet(TasteProfileResponse::notStarted);
    }

    private List<String> toKeywords(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return jsonMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to deserialize taste analysis keywords.", e);
        }
    }

    private String toJson(List<String> values) {
        try {
            return jsonMapper.writeValueAsString(values);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize taste analysis keywords.", e);
        }
    }

    private void validateLoginAvailableUser(User user) {
        switch (user.getStatus()) {
            case ACTIVE, ONBOARDING -> {
            }
            case BLOCKED -> throw new BusinessException(UserErrorCode.USER_BLOCKED);
            case WITHDRAWN -> throw new BusinessException(UserErrorCode.USER_WITHDRAWN);
        }
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
    }

    private void validateActiveUser(User user) {
        validateLoginAvailableUser(user);
        if (user.getStatus() == UserStatus.ONBOARDING) {
            throw new BusinessException(UserErrorCode.USER_ONBOARDING_REQUIRED);
        }
    }

    private String normalizeNickname(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            return DEFAULT_NICKNAME;
        }
        return nickname.length() > MAX_NICKNAME_LENGTH
                ? nickname.substring(0, MAX_NICKNAME_LENGTH) : nickname;
    }
}
