package kr.ktb.zura.needu.anonymouschat.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "needu.anonymous-chat.alias")
public record AnonymousAliasProperties(
        @NotEmpty List<@NotBlank String> modifiers,
        @NotEmpty List<@NotBlank String> animals
) {
}
