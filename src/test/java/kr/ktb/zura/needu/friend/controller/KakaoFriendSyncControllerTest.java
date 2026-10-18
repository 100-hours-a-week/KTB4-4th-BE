package kr.ktb.zura.needu.friend.controller;

import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import kr.ktb.zura.needu.auth.service.KakaoOAuthTransactionService;
import kr.ktb.zura.needu.auth.type.KakaoOAuthContext;
import kr.ktb.zura.needu.common.exception.GlobalExceptionHandler;
import kr.ktb.zura.needu.friend.service.FriendService;
import kr.ktb.zura.needu.friend.service.KakaoFriendSyncService;
import kr.ktb.zura.needu.friend.service.PokeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class KakaoFriendSyncControllerTest {

    private KakaoFriendSyncService syncService;
    private KakaoOAuthTransactionService transactionService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        syncService = mock(KakaoFriendSyncService.class);
        transactionService = mock(KakaoOAuthTransactionService.class);
        when(transactionService.getTtl()).thenReturn(Duration.ofMinutes(10));
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new FriendController(mock(FriendService.class), syncService, mock(PokeService.class),
                                transactionService, true))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void validReturnUrl_authorize_redirectsWithStoredState() throws Exception {
        when(transactionService.createFriendState(null, "https://needu.example.com/friends?tab=kakao"))
                .thenReturn("state-123");
        when(syncService.createAuthorizationUri(nullable(Long.class), anyString()))
                .thenReturn(URI.create("https://kauth.kakao.com/oauth/authorize?scope=friends"));

        mockMvc.perform(get("/api/v1/friends/kakao/authorize")
                        .principal(() -> "1")
                        .param("returnUrl", "https://needu.example.com/friends?tab=kakao"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", containsString("scope=friends")))
                .andExpect(header().string("Set-Cookie", containsString(
                        "NEEDU_KAKAO_FRIEND_OAUTH=state-123")));
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
        validTransaction();
        doThrow(new IllegalStateException()).when(syncService).sync(1L, "code");

        mockMvc.perform(get("/api/v1/friends/kakao/callback")
                        .cookie(oauthCookie()).param("code", "code").param("state", "state-123"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location",
                        "https://needu.example.com/friends?kakaoFriendSync=failed"))
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));
    }

    @Test
    void cancelledConsent_callback_redirectsCancelled() throws Exception {
        validTransaction();
        mockMvc.perform(get("/api/v1/friends/kakao/callback")
                        .cookie(oauthCookie()).param("state", "state-123")
                        .param("error", "access_denied"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location",
                        "https://needu.example.com/friends?kakaoFriendSync=cancelled"));
    }

    private void validTransaction() {
        when(transactionService.consumeFriendState("state-123", "state-123"))
                .thenReturn(Optional.of(new KakaoOAuthContext(
                        "https://needu.example.com/friends", 1L)));
    }

    private Cookie oauthCookie() {
        return new Cookie("NEEDU_KAKAO_FRIEND_OAUTH", "state-123");
    }
}
