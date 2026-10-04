package kr.ktb.zura.needu.common.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import kr.ktb.zura.needu.common.security.AuthCookieNames;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    static final String ACCESS_TOKEN_SCHEME = "accessTokenCookie";

    @Bean
    public OpenAPI needuOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("NeedU API")
                        .version("v1")
                        .description("dev 브랜치 기준으로 자동 생성되는 API 문서"))
                // 스펙은 CI의 MockMvc에서 생성되므로 요청 URL 대신 로컬 실행 주소를 명시
                .servers(List.of(new Server().url("http://localhost:8080").description("로컬 실행")))
                .components(new Components().addSecuritySchemes(ACCESS_TOKEN_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.COOKIE)
                        .name(AuthCookieNames.ACCESS_TOKEN)))
                .addSecurityItem(new SecurityRequirement().addList(ACCESS_TOKEN_SCHEME));
    }
}
