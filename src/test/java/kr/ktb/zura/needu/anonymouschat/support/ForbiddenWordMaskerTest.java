package kr.ktb.zura.needu.anonymouschat.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ForbiddenWordMaskerTest {

    private final ForbiddenWordMasker forbiddenWordMasker = new ForbiddenWordMasker();

    @Test
    void koreanForbiddenWord_mask_replacesEachCharacterWithAsterisk() {
        assertThat(forbiddenWordMasker.mask("이 씨발 뭐야")).isEqualTo("이 ** 뭐야");
    }

    @Test
    void englishForbiddenWord_mask_replacesEachCharacterWithAsterisk() {
        assertThat(forbiddenWordMasker.mask("fuck you")).isEqualTo("**** you");
    }

    @Test
    void normalSentence_mask_returnsSameContent() {
        assertThat(forbiddenWordMasker.mask("선물 고마워요 진짜 마음에 들어요")).isEqualTo("선물 고마워요 진짜 마음에 들어요");
    }

    @Test
    void everydayWordInForbiddenList_mask_replacesEachCharacterWithAsterisk() {
        assertThat(forbiddenWordMasker.mask("새끼 고양이 사진 보지 마")).isEqualTo("** 고양이 사진 ** 마");
    }

    @Test
    void overlappingForbiddenWords_mask_replacesWholeWord() {
        assertThat(forbiddenWordMasker.mask("개새끼야")).isEqualTo("***야");
    }

    @Test
    void repeatedForbiddenWord_mask_replacesEveryOccurrence() {
        assertThat(forbiddenWordMasker.mask("씨발 씨발")).isEqualTo("** **");
    }
}
