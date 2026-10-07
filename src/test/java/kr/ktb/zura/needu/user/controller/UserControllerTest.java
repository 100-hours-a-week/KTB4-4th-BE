package kr.ktb.zura.needu.user.controller;

import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.util.List;
import kr.ktb.zura.needu.user.dto.response.LinkedAccountResponse;
import kr.ktb.zura.needu.user.dto.response.MyPageResponse;
import kr.ktb.zura.needu.user.dto.response.MyProfileResponse;
import kr.ktb.zura.needu.user.dto.response.TasteProfileResponse;
import kr.ktb.zura.needu.user.service.UserService;
import kr.ktb.zura.needu.user.service.UserWithdrawalService;
import kr.ktb.zura.needu.user.type.AccountLinkStatus;
import kr.ktb.zura.needu.user.type.AccountProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
class UserControllerTest {

    private static final Long LOGIN_USER_ID = 1L;
    private static final String URL = "/api/v1/users/me";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private UserWithdrawalService userWithdrawalService;

    @Test
    void activeUser_findMyPage_returnsProfileAndTasteProfile() throws Exception {
        MyProfileResponse profile = new MyProfileResponse(LOGIN_USER_ID, "가나다", null, LocalDate.of(2000, 1, 1),
                new LinkedAccountResponse(AccountProvider.KAKAO, AccountLinkStatus.CONNECTED));
        given(userService.findMyPage(LOGIN_USER_ID))
                .willReturn(new MyPageResponse(profile, TasteProfileResponse.notStarted()));

        mockMvc.perform(get(URL).with(authenticatedUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("내 정보를 조회했습니다."))
                .andExpect(jsonPath("$.data.user.name").value("가나다"))
                .andExpect(jsonPath("$.data.user.birthDate").value("2000-01-01"))
                .andExpect(jsonPath("$.data.user.linkedAccount.provider").value("KAKAO"))
                .andExpect(jsonPath("$.data.user.linkedAccount.status").value("CONNECTED"))
                .andExpect(jsonPath("$.data.tasteProfile.analysisStatus").value("NOT_STARTED"))
                .andExpect(jsonPath("$.data.tasteProfile.preferenceKeywords").isEmpty())
                .andExpect(jsonPath("$.data.tasteProfile.aiSummary.status").value("NOT_READY"))
                .andExpect(jsonPath("$.data.tasteProfile.aiSummary.content").isEmpty());
    }

    @Test
    void withdrawalSucceeded_withdraw_expiresAuthCookies() throws Exception {
        mockMvc.perform(delete(URL)
                        .cookie(new Cookie("NEEDU_REFRESH_TOKEN", "refresh-token"))
                        .with(authenticatedUser())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("회원 탈퇴가 완료되었습니다."))
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(header().stringValues(HttpHeaders.SET_COOKIE, containsInAnyOrder(
                        startsWith("NEEDU_ACCESS_TOKEN=; Path=/; Max-Age=0"),
                        startsWith("NEEDU_REFRESH_TOKEN=; Path=/; Max-Age=0"))));

        verify(userWithdrawalService).withdraw(LOGIN_USER_ID, "refresh-token");
    }

    private static RequestPostProcessor authenticatedUser() {
        return authentication(new UsernamePasswordAuthenticationToken(LOGIN_USER_ID, null, List.of()));
    }
}
