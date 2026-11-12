package kr.ktb.zura.needu.auth.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@EnableConfigurationProperties(DemoLoginProperties.class)
public class DemoLoginConfig {

    @Bean
    PasswordEncoder demoLoginPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
