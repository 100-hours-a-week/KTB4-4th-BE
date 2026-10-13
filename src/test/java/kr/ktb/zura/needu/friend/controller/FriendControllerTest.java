package kr.ktb.zura.needu.friend.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.response.RetryAfterResponse;
import kr.ktb.zura.needu.friend.dto.request.FriendSearchCondition;
import kr.ktb.zura.needu.friend.dto.response.FriendDetailResponse;
import kr.ktb.zura.needu.friend.dto.response.FriendFavoriteResponse;
import kr.ktb.zura.needu.friend.dto.response.FriendListResponse;
import kr.ktb.zura.needu.friend.dto.response.FriendSummaryResponse;
import kr.ktb.zura.needu.friend.dto.response.PokeResponse;
import kr.ktb.zura.needu.friend.exception.FriendErrorCode;
import kr.ktb.zura.needu.friend.service.FriendService;
import kr.ktb.zura.needu.friend.service.KakaoFriendSyncService;
import kr.ktb.zura.needu.friend.service.PokeService;
import kr.ktb.zura.needu.user.exception.UserErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FriendController.class)
class FriendControllerTest {

    private static final Long LOGIN_USER_ID = 1L;
    private static final Long FRIEND_USER_ID = 123L;
    private static final String URL = "/api/v1/friends/{userId}";
    private static final String LIST_URL = "/api/v1/friends";
    private static final String FAVORITE_URL = "/api/v1/friends/{userId}/favorite";
    private static final String POKE_URL = "/api/v1/friends/{userId}/pokes";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FriendService friendService;

    @MockitoBean
    private KakaoFriendSyncService kakaoFriendSyncService;

    @MockitoBean
    private PokeService pokeService;

    @Test
    void friendsExist_findAllFriends_returnsFriendPage() throws Exception {
        FriendSummaryResponse friend = new FriendSummaryResponse(
                FRIEND_USER_ID, "친구", "https://example.com/profile.jpg", LocalDate.of(2000, 2, 29), true);
        given(friendService.findAllFriends(LOGIN_USER_ID, new FriendSearchCondition(null, null, null, 20)))
                .willReturn(new FriendListResponse(List.of(friend), true, 12L, 4L, "next", true));

        mockMvc.perform(get(LIST_URL)
                        .param("size", "20")
                        .with(authenticatedUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("친구 목록 조회에 성공했습니다."))
                .andExpect(jsonPath("$.data.items[0].userId").value(FRIEND_USER_ID))
                .andExpect(jsonPath("$.data.items[0].name").value("친구"))
                .andExpect(jsonPath("$.data.items[0].birthDate").value("2000-02-29"))
                .andExpect(jsonPath("$.data.items[0].isFavorite").value(true))
                .andExpect(jsonPath("$.data.isKakaoFriendSynced").value(true))
                .andExpect(jsonPath("$.totalCount").value(12))
                .andExpect(jsonPath("$.favoriteTotalCount").value(4))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.nextCursor").value("next"));
    }

    @Test
    void favoriteFalse_findAllFriends_passesFavoriteConditionAndReturnsNamePage() throws Exception {
        FriendSummaryResponse friend = new FriendSummaryResponse(FRIEND_USER_ID, "가나", null, null, true);
        given(friendService.findAllFriends(LOGIN_USER_ID, new FriendSearchCondition(false, null, "cursor", 20)))
                .willReturn(new FriendListResponse(List.of(friend), false, 12L, 4L, "next", true));

        mockMvc.perform(get(LIST_URL)
                        .param("favorite", "false")
                        .param("cursor", "cursor")
                        .param("size", "20")
                        .with(authenticatedUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].name").value("가나"))
                .andExpect(jsonPath("$.data.items[0].isFavorite").value(true))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.nextCursor").value("next"));
    }

    @Test
    void favoriteTrue_findAllFriends_passesFavoriteCondition() throws Exception {
        FriendSummaryResponse friend = new FriendSummaryResponse(FRIEND_USER_ID, "가나", null, null, true);
        given(friendService.findAllFriends(LOGIN_USER_ID, new FriendSearchCondition(true, null, null, 20)))
                .willReturn(new FriendListResponse(List.of(friend), false, 12L, 4L, null, false));

        mockMvc.perform(get(LIST_URL)
                        .param("favorite", "true")
                        .param("size", "20")
                        .with(authenticatedUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].isFavorite").value(true))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void keyword_findAllFriends_passesKeywordCondition() throws Exception {
        FriendSummaryResponse friend = new FriendSummaryResponse(FRIEND_USER_ID, "김철수", null, null, false);
        given(friendService.findAllFriends(LOGIN_USER_ID, new FriendSearchCondition(null, "김", null, 20)))
                .willReturn(new FriendListResponse(List.of(friend), false, 12L, 4L, null, false));

        mockMvc.perform(get(LIST_URL)
                        .param("keyword", "김")
                        .param("size", "20")
                        .with(authenticatedUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].name").value("김철수"));
    }

    @Test
    void missingFavorite_findAllFriends_returnsDefaultNamePage() throws Exception {
        given(friendService.findAllFriends(LOGIN_USER_ID, new FriendSearchCondition(null, null, null, 20)))
                .willReturn(new FriendListResponse(List.of(), false, 0L, 0L, null, false));

        mockMvc.perform(get(LIST_URL).param("size", "20").with(authenticatedUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty());
    }

    @Test
    void sizeOutOfRange_findAllFriends_returnsUnprocessableContent() throws Exception {
        mockMvc.perform(get(LIST_URL)
                        .param("size", "51")
                        .with(authenticatedUser()))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void missingSize_findAllFriends_returnsUnprocessableContent() throws Exception {
        mockMvc.perform(get(LIST_URL).with(authenticatedUser()))
                .andExpect(status().isUnprocessableContent());

        verify(friendService, never()).findAllFriends(anyLong(), any());
    }

    @Test
    void nonNumericSize_findAllFriends_returnsBadRequest() throws Exception {
        mockMvc.perform(get(LIST_URL)
                        .param("size", "twenty")
                        .with(authenticatedUser()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nonBooleanFavorite_findAllFriends_returnsBadRequest() throws Exception {
        mockMvc.perform(get(LIST_URL)
                        .param("favorite", "maybe")
                        .param("size", "20")
                        .with(authenticatedUser()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void keywordOverTwentyCharacters_findAllFriends_returnsUnprocessableContent() throws Exception {
        mockMvc.perform(get(LIST_URL)
                        .param("keyword", "가".repeat(21))
                        .param("size", "20")
                        .with(authenticatedUser()))
                .andExpect(status().isUnprocessableContent());

        verify(friendService, never()).findAllFriends(anyLong(), any());
    }

    @Test
    void blankKeyword_findAllFriends_returnsUnprocessableContent() throws Exception {
        mockMvc.perform(get(LIST_URL)
                        .param("keyword", "   ")
                        .param("size", "20")
                        .with(authenticatedUser()))
                .andExpect(status().isUnprocessableContent());

        verify(friendService, never()).findAllFriends(anyLong(), any());
    }

    @Test
    void friendExists_findFriend_returnsFriendUser() throws Exception {
        given(friendService.findFriend(LOGIN_USER_ID, FRIEND_USER_ID))
                .willReturn(new FriendDetailResponse(FRIEND_USER_ID, "사용자", null, LocalDate.of(2000, 2, 29), true,
                        List.of("미니멀"), List.of("러닝"), "러닝을 좋아해요.", true));

        mockMvc.perform(get(URL, FRIEND_USER_ID).with(authenticatedUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("친구 정보를 조회했습니다."))
                .andExpect(jsonPath("$.data.userId").value(123))
                .andExpect(jsonPath("$.data.name").value("사용자"))
                .andExpect(jsonPath("$.data.profileImageUrl").isEmpty())
                .andExpect(jsonPath("$.data.tasteAnalysisCompleted").value(true))
                .andExpect(jsonPath("$.data.birthDate").value("2000-02-29"))
                .andExpect(jsonPath("$.data.tasteKeywords[0]").value("미니멀"))
                .andExpect(jsonPath("$.data.interestKeywords[0]").value("러닝"))
                .andExpect(jsonPath("$.data.interestSummary").value("러닝을 좋아해요."))
                .andExpect(jsonPath("$.data.isFavorite").value(true));
    }

    @Test
    void friendExists_updateFavorite_returnsFavoriteState() throws Exception {
        given(friendService.updateFavorite(LOGIN_USER_ID, FRIEND_USER_ID, true))
                .willReturn(new FriendFavoriteResponse(FRIEND_USER_ID, true));

        mockMvc.perform(put(FAVORITE_URL, FRIEND_USER_ID)
                        .with(authenticatedUser())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isFavorite\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("즐겨찾기를 변경했습니다."))
                .andExpect(jsonPath("$.data.userId").value(123))
                .andExpect(jsonPath("$.data.isFavorite").value(true));
    }

    @Test
    void missingIsFavorite_updateFavorite_returnsUnprocessableContent() throws Exception {
        mockMvc.perform(put(FAVORITE_URL, FRIEND_USER_ID)
                        .with(authenticatedUser())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableContent());

        verify(friendService, never()).updateFavorite(anyLong(), anyLong(), anyBoolean());
    }

    @Test
    void nonBooleanIsFavorite_updateFavorite_returnsBadRequest() throws Exception {
        mockMvc.perform(put(FAVORITE_URL, FRIEND_USER_ID)
                        .with(authenticatedUser())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isFavorite\": \"yes\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void friendNotFound_findFriend_returnsNotFound() throws Exception {
        given(friendService.findFriend(LOGIN_USER_ID, FRIEND_USER_ID))
                .willThrow(new BusinessException(FriendErrorCode.FRIEND_NOT_FOUND));

        mockMvc.perform(get(URL, FRIEND_USER_ID).with(authenticatedUser()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("친구를 찾을 수 없습니다."))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void nonNumericUserId_findFriend_returnsBadRequest() throws Exception {
        mockMvc.perform(get(URL, "abc").with(authenticatedUser()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("요청 형식이 올바르지 않습니다."));

        verify(friendService, never()).findFriend(anyLong(), anyLong());
    }

    @Test
    void friendExists_createPoke_returnsCreated() throws Exception {
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 11, 12, 0);
        given(pokeService.createPoke(LOGIN_USER_ID, FRIEND_USER_ID))
                .willReturn(new PokeResponse(FRIEND_USER_ID, createdAt, createdAt.plusHours(1)));

        mockMvc.perform(post(POKE_URL, FRIEND_USER_ID).with(authenticatedUser()).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("친구를 콕 찔렀어요."))
                .andExpect(jsonPath("$.data.friendUserId").value(FRIEND_USER_ID))
                .andExpect(jsonPath("$.data.createdAt").value("2026-10-11T12:00:00"))
                .andExpect(jsonPath("$.data.nextPokeAvailableAt").value("2026-10-11T13:00:00"));
    }

    @Test
    void blockedUser_createPoke_returnsForbidden() throws Exception {
        given(pokeService.createPoke(LOGIN_USER_ID, FRIEND_USER_ID))
                .willThrow(new BusinessException(UserErrorCode.USER_BLOCKED));

        mockMvc.perform(post(POKE_URL, FRIEND_USER_ID).with(authenticatedUser()).with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("이용이 제한된 계정입니다."))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void onboardingUser_createPoke_returnsForbidden() throws Exception {
        given(pokeService.createPoke(LOGIN_USER_ID, FRIEND_USER_ID))
                .willThrow(new BusinessException(UserErrorCode.USER_ONBOARDING_REQUIRED));

        mockMvc.perform(post(POKE_URL, FRIEND_USER_ID).with(authenticatedUser()).with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("온보딩 진행이 필요합니다."))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void notFriend_createPoke_returnsNotFound() throws Exception {
        given(pokeService.createPoke(LOGIN_USER_ID, FRIEND_USER_ID))
                .willThrow(new BusinessException(FriendErrorCode.FRIEND_NOT_FOUND));

        mockMvc.perform(post(POKE_URL, FRIEND_USER_ID).with(authenticatedUser()).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("친구를 찾을 수 없습니다."))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void unexpectedError_createPoke_returnsInternalServerError() throws Exception {
        given(pokeService.createPoke(LOGIN_USER_ID, FRIEND_USER_ID))
                .willThrow(new IllegalStateException("unexpected"));

        mockMvc.perform(post(POKE_URL, FRIEND_USER_ID).with(authenticatedUser()).with(csrf()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("요청을 처리하지 못했습니다."))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void cooldownExists_createPoke_returnsTooManyRequests() throws Exception {
        given(pokeService.createPoke(LOGIN_USER_ID, FRIEND_USER_ID))
                .willThrow(new BusinessException(
                        FriendErrorCode.FRIEND_POKE_TOO_FREQUENT,
                        new RetryAfterResponse(3540L)
                ));

        mockMvc.perform(post(POKE_URL, FRIEND_USER_ID).with(authenticatedUser()).with(csrf()))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."))
                .andExpect(jsonPath("$.data.retryAfterSeconds").value(3540));
    }

    private static RequestPostProcessor authenticatedUser() {
        return authentication(new UsernamePasswordAuthenticationToken(LOGIN_USER_ID, null, List.of()));
    }
}
