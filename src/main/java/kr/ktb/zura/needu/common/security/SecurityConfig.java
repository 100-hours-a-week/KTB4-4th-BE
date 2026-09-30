package kr.ktb.zura.needu.common.security;

import java.util.List;
import org.springframework.core.convert.converter.Converter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.NullAuthenticatedSessionStrategy;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.web.util.WebUtils;

@Configuration
public class SecurityConfig {

    private static final String HEALTH_PATH = "/actuator/health";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            RestAuthenticationEntryPoint authenticationEntryPoint,
                                            RestAccessDeniedHandler accessDeniedHandler,
                                            @Value("${auth.cookie.secure}") boolean cookieSecure) {
        CookieCsrfTokenRepository csrfTokens = new CookieCsrfTokenRepository();
        csrfTokens.setCookieName(cookieSecure ? "__Host-NEEDU_CSRF" : "NEEDU_CSRF");
        csrfTokens.setCookieCustomizer(cookie -> cookie.httpOnly(true).secure(cookieSecure)
                .sameSite("Lax").path("/"));
        BearerTokenResolver cookieTokenResolver = request -> {
            String path = request.getRequestURI().substring(request.getContextPath().length());
            if (path.startsWith("/api/v1/auth/kakao/") || path.equals("/api/v1/auth/csrf")
                    || path.equals("/api/v1/auth/refresh") || path.equals("/api/v1/auth/logout")
                    || path.equals("/api/v1/friends/kakao/callback") || path.equals(HEALTH_PATH)) {
                return null;
            }
            var cookie = WebUtils.getCookie(request, AuthCookieNames.ACCESS_TOKEN);
            return cookie == null ? null : cookie.getValue();
        };
        return http
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.GET, HEALTH_PATH).permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/metrics/hikaricp.connections.active",
                                "/actuator/metrics/hikaricp.connections.pending").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/kakao/authorize", "/api/v1/auth/kakao/callback")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/friends/kakao/callback").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/refresh", "/api/v1/auth/logout")
                        .permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                        .bearerTokenResolver(cookieTokenResolver)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .csrf(csrf -> csrf.csrfTokenRepository(csrfTokens)
                        // STATELESS에서 인증된 요청마다 새 로그인으로 판단돼 기본 전략이 CSRF 쿠키를 매번 삭제하므로 끈다.
                        .sessionAuthenticationStrategy(new NullAuthenticatedSessionStrategy())
                        // 리소스 서버는 베어러 토큰이 포함된 요청을 CSRF 검사에서 제외
                        // 액세스 토큰 활성화된 상태에서 CSRF 검사 X => 모든 상태 변경 요청을 검사하도록
                        .withObjectPostProcessor(new ObjectPostProcessor<CsrfFilter>() {
                            @Override
                            public <O extends CsrfFilter> O postProcess(O filter) {
                                filter.setRequireCsrfProtectionMatcher(CsrfFilter.DEFAULT_CSRF_MATCHER);
                                return filter;
                            }
                        }))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .build();
    }

    private Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter() {
        return jwt -> new UsernamePasswordAuthenticationToken(
                ((Number) jwt.getClaim("userId")).longValue(), null, List.of());
    }
}
