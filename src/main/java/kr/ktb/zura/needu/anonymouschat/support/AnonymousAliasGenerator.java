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

    private final List<AliasCandidate> candidates;

    public AnonymousAliasGenerator(AnonymousAliasProperties properties) {
        this.candidates = properties.modifiers().stream()
                .flatMap(modifier -> properties.animals().stream()
                        .map(animal -> new AliasCandidate(
                                modifier + " " + animal.name(),
                                animal.avatar(),
                                findAvatarColors(animal, properties.avatarColors()))))
                .distinct()
                .toList();
        if (candidates.stream().anyMatch(candidate -> candidate.name().length() > AnonymousRoom.QUESTIONER_ALIAS_MAX_LENGTH)) {
            throw new IllegalArgumentException("익명대화 상대명은 최대 20자여야 합니다.");
        }
    }

    public AnonymousAlias generate(Set<String> usedAliases) {
        List<AliasCandidate> availableCandidates = candidates.stream()
                .filter(candidate -> !usedAliases.contains(candidate.name()))
                .toList();
        if (availableCandidates.isEmpty()) {
            throw new BusinessException(AnonymousChatErrorCode.ANONYMOUS_CHAT_ALIAS_EXHAUSTED);
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        AliasCandidate candidate = availableCandidates.get(random.nextInt(availableCandidates.size()));
        String avatarColor = candidate.avatarColors().get(random.nextInt(candidate.avatarColors().size()));
        return new AnonymousAlias(candidate.name(), candidate.avatarAnimal(), avatarColor);
    }

    // 털색과 겹치는 배경색을 뺀 아바타 색 목록
    private static List<String> findAvatarColors(AnonymousAliasProperties.Animal animal, List<String> avatarColors) {
        if (!avatarColors.containsAll(animal.excludedColors())) {
            throw new IllegalArgumentException("익명대화 아바타 제외 색은 avatar-colors에 있어야 합니다: " + animal.avatar());
        }
        List<String> colors = avatarColors.stream()
                .filter(color -> !animal.excludedColors().contains(color))
                .toList();
        if (colors.isEmpty()) {
            throw new IllegalArgumentException("익명대화 아바타에 사용할 색이 없습니다: " + animal.avatar());
        }
        return colors;
    }

    private record AliasCandidate(String name, String avatarAnimal, List<String> avatarColors) {
    }
}
