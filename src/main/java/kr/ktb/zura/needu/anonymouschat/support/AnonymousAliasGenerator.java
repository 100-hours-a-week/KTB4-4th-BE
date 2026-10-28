package kr.ktb.zura.needu.anonymouschat.support;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import kr.ktb.zura.needu.anonymouschat.config.AnonymousAliasProperties;
import kr.ktb.zura.needu.anonymouschat.entity.AnonymousRoom;
import kr.ktb.zura.needu.anonymouschat.exception.AnonymousChatErrorCode;
import kr.ktb.zura.needu.common.exception.BusinessException;
import org.springframework.stereotype.Component;

@Component
public class AnonymousAliasGenerator {

    private final List<String> aliases;

    public AnonymousAliasGenerator(AnonymousAliasProperties properties) {
        this.aliases = properties.modifiers().stream()
                .flatMap(modifier -> properties.animals().stream().map(animal -> modifier + " " + animal))
                .distinct()
                .toList();
        if (aliases.stream().anyMatch(alias -> alias.length() > AnonymousRoom.QUESTIONER_ALIAS_MAX_LENGTH)) {
            throw new IllegalArgumentException("익명대화 상대명은 최대 20자여야 합니다.");
        }
    }

    public String generate(Set<String> usedAliases) {
        List<String> availableAliases = aliases.stream()
                .filter(alias -> !usedAliases.contains(alias))
                .toList();
        if (availableAliases.isEmpty()) {
            throw new BusinessException(AnonymousChatErrorCode.ANONYMOUS_CHAT_ALIAS_EXHAUSTED);
        }
        return availableAliases.get(ThreadLocalRandom.current().nextInt(availableAliases.size()));
    }
}
