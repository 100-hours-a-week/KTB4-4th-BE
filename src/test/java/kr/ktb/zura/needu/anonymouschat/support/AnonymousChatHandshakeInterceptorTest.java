package kr.ktb.zura.needu.anonymouschat.support;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.user.exception.UserErrorCode;
import kr.ktb.zura.needu.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.socket.WebSocketHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class AnonymousChatHandshakeInterceptorTest {

    private static final Long USER_ID = 1L;

    private UserService userService;
    private AnonymousChatHandshakeInterceptor interceptor;
    private WebSocketHandler webSocketHandler;

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        interceptor = new AnonymousChatHandshakeInterceptor(userService);
        webSocketHandler = mock(WebSocketHandler.class);
    }

    @Test
    void activeUser_beforeHandshake_allowsConnection() {
        HandshakeRequest request = authenticatedRequest();

        boolean result = interceptor.beforeHandshake(
                request.request(), request.response(), webSocketHandler, Map.of());

        assertThat(result).isTrue();
        verify(userService).validateActiveUser(USER_ID);
    }

    @Test
    void blockedUser_beforeHandshake_rejectsConnection() {
        HandshakeRequest request = authenticatedRequest();
        willThrow(new BusinessException(UserErrorCode.USER_BLOCKED))
                .given(userService).validateActiveUser(USER_ID);

        boolean result = interceptor.beforeHandshake(
                request.request(), request.response(), webSocketHandler, Map.of());

        assertThat(result).isFalse();
        assertThat(request.servletResponse().getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
    }

    @Test
    void unauthenticatedUser_beforeHandshake_rejectsConnection() {
        HandshakeRequest request = request(null);

        boolean result = interceptor.beforeHandshake(
                request.request(), request.response(), webSocketHandler, Map.of());

        assertThat(result).isFalse();
        assertThat(request.servletResponse().getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        verifyNoInteractions(userService);
    }

    private HandshakeRequest authenticatedRequest() {
        return request(UsernamePasswordAuthenticationToken.authenticated(USER_ID, null, List.of()));
    }

    private HandshakeRequest request(Principal principal) {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setUserPrincipal(principal);
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();
        return new HandshakeRequest(
                new ServletServerHttpRequest(servletRequest),
                new ServletServerHttpResponse(servletResponse),
                servletResponse);
    }

    private record HandshakeRequest(
            ServletServerHttpRequest request,
            ServletServerHttpResponse response,
            MockHttpServletResponse servletResponse) {
    }
}
