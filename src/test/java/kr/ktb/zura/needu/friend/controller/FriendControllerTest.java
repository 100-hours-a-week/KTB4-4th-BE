package kr.ktb.zura.needu.friend.controller;

import java.time.LocalDate;
import java.util.List;

import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.common.response.CursorPageResponse;
import kr.ktb.zura.needu.friend.dto.response.FriendDetailResponse;
import kr.ktb.zura.needu.friend.dto.response.FriendSummaryResponse;
import kr.ktb.zura.needu.friend.exception.FriendErrorCode;
import kr.ktb.zura.needu.friend.service.FriendService;
import kr.ktb.zura.needu.friend.service.KakaoFriendSyncService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FriendController.class)
class FriendControllerTest {

    private static final Long LOGIN_USER_ID = 1L;
    private static final Long FRIEND_USER_ID = 123L;
    private static final String URL = "/api/v1/friends/{userId}";
    private static final String LIST_URL = "/api/v1/friends";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FriendService friendService;

    @MockitoBean
    private KakaoFriendSyncService kakaoFriendSyncService;

    @Test
    void friendsExist_findAllFriends_returnsBirthdayPage() throws Exception {
        FriendSummaryResponse friend = new FriendSummaryResponse(
                FRIEND_USER_ID, "친구", "https://example.com/profile.jpg", LocalDate.of(2000, 2, 29), true);
        given(friendService.findAllFriends(LOGIN_USER_ID, null, 20))
                .willReturn(new CursorPageResponse<>(List.of(friend), "next", true));

        mockMvc.perform(get(LIST_URL)
                        .param("sort", "birthday")
                        .param("size", "20")
                        .with(authenticatedUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("친구 목록 조회에 성공했습니다."))
                .andExpect(jsonPath("$.data.items[0].userId").value(FRIEND_USER_ID))
                .andExpect(jsonPath("$.data.items[0].name").value("친구"))
                .andExpect(jsonPath("$.data.items[0].birthDate").value("2000-02-29"))
                .andExpect(jsonPath("$.data.items[0].isFavorite").value(true))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.nextCursor").value("next"));
    }

    @Test
    void missingSort_findAllFriends_returnsBadRequest() throws Exception {
        mockMvc.perform(get(LIST_URL).param("size", "20").with(authenticatedUser()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("요청 형식이 올바르지 않습니다."));
    }

    @Test
    void unsupportedSort_findAllFriends_returnsUnprocessableContent() throws Exception {
        mockMvc.perform(get(LIST_URL)
                        .param("sort", "name")
                        .param("size", "20")
                        .with(authenticatedUser()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message")
                        .value("입력값이 유효하지 않습니다. 입력 내용을 확인해 주세요."));
    }

    @Test
    void sizeOutOfRange_findAllFriends_returnsUnprocessableContent() throws Exception {
        mockMvc.perform(get(LIST_URL)
                        .param("sort", "birthday")
                        .param("size", "51")
                        .with(authenticatedUser()))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void friendExists_findFriend_returnsFriendUser() throws Exception {
        given(friendService.findFriend(LOGIN_USER_ID, FRIEND_USER_ID))
                .willReturn(new FriendDetailResponse(FRIEND_USER_ID, "사용자", null, true, LocalDate.of(2000, 2, 29)));

        mockMvc.perform(get(URL, FRIEND_USER_ID).with(authenticatedUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("친구 정보를 조회했습니다."))
                .andExpect(jsonPath("$.data.id").value(123))
                .andExpect(jsonPath("$.data.nickname").value("사용자"))
                .andExpect(jsonPath("$.data.profileImageUrl").isEmpty())
                .andExpect(jsonPath("$.data.tasteAnalysisCompleted").value(true))
                .andExpect(jsonPath("$.data.birthDate").value("2000-02-29"));
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

    private static RequestPostProcessor authenticatedUser() {
        return authentication(new UsernamePasswordAuthenticationToken(LOGIN_USER_ID, null, List.of()));
    }
}
