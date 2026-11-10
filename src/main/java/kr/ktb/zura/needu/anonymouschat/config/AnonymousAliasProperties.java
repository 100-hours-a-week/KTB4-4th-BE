package kr.ktb.zura.needu.anonymouschat.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "needu.anonymous-chat.alias")
public record AnonymousAliasProperties(
        @NotEmpty List<@NotBlank String> modifiers,
        @NotEmpty List<@Valid Animal> animals,
        @NotEmpty List<@NotBlank String> avatarColors
) {

    public record Animal(
            @NotBlank String name,
            @NotBlank String avatar,
            List<String> excludedColors
    ) {

        public Animal {
            excludedColors = excludedColors == null ? List.of() : List.copyOf(excludedColors);
        }
    }
}
