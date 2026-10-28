package kr.ktb.zura.needu.anonymouschat.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import kr.ktb.zura.needu.anonymouschat.config.AnonymousAliasProperties;
import kr.ktb.zura.needu.anonymouschat.entity.AnonymousRoom;
import kr.ktb.zura.needu.anonymouschat.exception.AnonymousChatErrorCode;
import kr.ktb.zura.needu.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

class AnonymousAliasGeneratorTest {

    private static final int ALIAS_COUNT = 4;

    private final AnonymousAliasGenerator generator = new AnonymousAliasGenerator(new AnonymousAliasProperties(
            List.of("마음을 전하는", "취향이 궁금한"),
            List.of("수달", "여우")
    ));

    @Test
    void generate_usedAliasesExcluded_returnsAllAliasesWithoutDuplicates() {
        Set<String> usedAliases = new HashSet<>();

        for (int count = 0; count < ALIAS_COUNT; count++) {
            String alias = generator.generate(usedAliases);

            assertThat(alias).hasSizeLessThanOrEqualTo(AnonymousRoom.QUESTIONER_ALIAS_MAX_LENGTH);
            assertThat(usedAliases.add(alias)).isTrue();
        }

        assertThat(usedAliases).hasSize(ALIAS_COUNT);
    }

    @Test
    void generate_allAliasesUsed_throwsBusinessException() {
        Set<String> usedAliases = new HashSet<>();
        for (int count = 0; count < ALIAS_COUNT; count++) {
            usedAliases.add(generator.generate(usedAliases));
        }

        assertThatThrownBy(() -> generator.generate(usedAliases))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(AnonymousChatErrorCode.ANONYMOUS_CHAT_ALIAS_EXHAUSTED));
    }
}
