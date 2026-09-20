package kr.ktb.zura.needu.friend.controller;

import java.net.URI;
import kr.ktb.zura.needu.common.exception.GlobalExceptionHandler;
import kr.ktb.zura.needu.friend.service.FriendService;
import kr.ktb.zura.needu.friend.service.KakaoFriendSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class KakaoFriendSyncControllerTest {

    private KakaoFriendSyncService syncService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        syncService = mock(KakaoFriendSyncService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new FriendController(mock(FriendService.class), syncService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void validReturnUrl_authorize_redirectsWithStoredState() throws Exception {
        when(syncService.createAuthorizationUri(anyString()))
                .thenReturn(URI.create("https://kauth.kakao.com/oauth/authorize?scope=friends"));

        MockHttpSession session = (MockHttpSession) mockMvc.perform(get("/api/v1/friends/kakao/authorize")
                        .principal(() -> "1")
                        .param("returnUrl", "https://needu.example.com/friends?tab=kakao"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", containsString("scope=friends")))
                .andReturn().getRequest().getSession(false);

        org.junit.jupiter.api.Assertions.assertEquals(
                URI.create("https://needu.example.com/friends?tab=kakao"),
                session.getAttribute("kakaoFriendOAuthReturnUrl"));
    }

    @Test
    void invalidReturnUrl_authorize_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/friends/kakao/authorize")
                        .principal(() -> "1")
                        .param("returnUrl", "javascript:alert(1)"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void syncFailure_callback_redirectsFailed() throws Exception {
        MockHttpSession session = friendSession();
        doThrow(new IllegalStateException()).when(syncService).sync(1L, "code");

        mockMvc.perform(get("/api/v1/friends/kakao/callback")
                        .session(session).param("code", "code").param("state", "state-123"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location",
                        "https://needu.example.com/friends?kakaoFriendSync=failed"))
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    void cancelledConsent_callback_redirectsCancelled() throws Exception {
        mockMvc.perform(get("/api/v1/friends/kakao/callback")
                        .session(friendSession()).param("state", "state-123")
                        .param("error", "access_denied"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location",
                        "https://needu.example.com/friends?kakaoFriendSync=cancelled"));
    }

    private MockHttpSession friendSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("kakaoFriendOAuthState", "state-123");
        session.setAttribute("kakaoFriendOAuthUserId", 1L);
        session.setAttribute("kakaoFriendOAuthReturnUrl", URI.create("https://needu.example.com/friends"));
        return session;
    }
}
