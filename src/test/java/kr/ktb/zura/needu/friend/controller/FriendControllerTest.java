package kr.ktb.zura.needu.friend.controller;

import java.util.List;

import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.friend.dto.response.FriendResponse;
import kr.ktb.zura.needu.friend.exception.FriendErrorCode;
import kr.ktb.zura.needu.friend.service.FriendService;
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

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FriendService friendService;

    @Test
    void friendExists_findFriend_returnsFriendUser() throws Exception {
        given(friendService.findFriend(LOGIN_USER_ID, FRIEND_USER_ID))
                .willReturn(new FriendResponse(FRIEND_USER_ID, "사용자", null, true));

        mockMvc.perform(get(URL, FRIEND_USER_ID).with(authenticatedUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("친구 정보를 조회했습니다."))
                .andExpect(jsonPath("$.data.id").value(123))
                .andExpect(jsonPath("$.data.nickname").value("사용자"))
                .andExpect(jsonPath("$.data.profileImageUrl").isEmpty())
                .andExpect(jsonPath("$.data.tasteAnalysisCompleted").value(true));
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
