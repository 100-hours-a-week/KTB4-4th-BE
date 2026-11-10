package kr.ktb.zura.needu.anonymouschat.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import kr.ktb.zura.needu.anonymouschat.config.AnonymousAliasProperties;
import kr.ktb.zura.needu.anonymouschat.config.AnonymousAliasProperties.Animal;
import kr.ktb.zura.needu.anonymouschat.entity.AnonymousRoom;
import kr.ktb.zura.needu.anonymouschat.exception.AnonymousChatErrorCode;
import kr.ktb.zura.needu.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

class AnonymousAliasGeneratorTest {

    private static final int ALIAS_COUNT = 4;
    private static final List<String> AVATAR_COLORS = List.of("mint", "peach", "sky");

    private final AnonymousAliasGenerator generator = new AnonymousAliasGenerator(new AnonymousAliasProperties(
            List.of("마음을 전하는", "취향이 궁금한"),
            List.of(new Animal("수달", "otter", null), new Animal("여우", "fox", List.of("peach"))),
            AVATAR_COLORS
    ));

    @Test
    void generate_usedAliasesExcluded_returnsAllAliasesWithoutDuplicates() {
        Set<String> usedAliases = new HashSet<>();

        for (int count = 0; count < ALIAS_COUNT; count++) {
            String alias = generator.generate(usedAliases).name();

            assertThat(alias).hasSizeLessThanOrEqualTo(AnonymousRoom.QUESTIONER_ALIAS_MAX_LENGTH);
            assertThat(usedAliases.add(alias)).isTrue();
        }

        assertThat(usedAliases).hasSize(ALIAS_COUNT);
    }

    @Test
    void generate_returnsAvatarOfAliasAnimalWithoutExcludedColors() {
        Map<String, String> avatarAnimals = Map.of("수달", "otter", "여우", "fox");

        for (int count = 0; count < 200; count++) {
            AnonymousAlias alias = generator.generate(Set.of());

            String animalName = alias.name().substring(alias.name().lastIndexOf(' ') + 1);
            assertThat(alias.avatarAnimal()).isEqualTo(avatarAnimals.get(animalName));
            assertThat(alias.avatarColor()).isIn(AVATAR_COLORS);
            if (alias.avatarAnimal().equals("fox")) {
                assertThat(alias.avatarColor()).isNotEqualTo("peach");
            }
        }
    }

    @Test
    void generate_allAliasesUsed_throwsBusinessException() {
        Set<String> usedAliases = new HashSet<>();
        for (int count = 0; count < ALIAS_COUNT; count++) {
            usedAliases.add(generator.generate(usedAliases).name());
        }

        assertThatThrownBy(() -> generator.generate(usedAliases))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(AnonymousChatErrorCode.ANONYMOUS_CHAT_ALIAS_EXHAUSTED));
    }

    @Test
    void excludedColorNotInAvatarColors_createGenerator_throwsIllegalArgumentException() {
        AnonymousAliasProperties properties = new AnonymousAliasProperties(
                List.of("마음을 전하는"), List.of(new Animal("수달", "otter", List.of("navy"))), AVATAR_COLORS);

        assertThatThrownBy(() -> new AnonymousAliasGenerator(properties))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void allColorsExcluded_createGenerator_throwsIllegalArgumentException() {
        AnonymousAliasProperties properties = new AnonymousAliasProperties(
                List.of("마음을 전하는"), List.of(new Animal("수달", "otter", AVATAR_COLORS)), AVATAR_COLORS);

        assertThatThrownBy(() -> new AnonymousAliasGenerator(properties))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
