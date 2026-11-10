package kr.ktb.zura.needu.anonymouschat.support;

import java.security.Principal;
import java.util.Map;

import kr.ktb.zura.needu.common.exception.BusinessException;
import kr.ktb.zura.needu.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

@Component
@RequiredArgsConstructor
public class AnonymousChatHandshakeInterceptor implements HandshakeInterceptor {

    private final UserService userService;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        Principal principal = request.getPrincipal();
        if (!(principal instanceof Authentication authentication)
                || !(authentication.getPrincipal() instanceof Long userId)) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        try {
            userService.validateActiveUser(userId);
            return true;
        } catch (BusinessException exception) {
            response.setStatusCode(exception.getErrorCode().getStatus());
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }
}
