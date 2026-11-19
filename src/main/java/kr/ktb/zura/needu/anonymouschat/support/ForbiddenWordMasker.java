package kr.ktb.zura.needu.anonymouschat.support;

import com.vane.badwordfiltering.BadWordFiltering;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class ForbiddenWordMasker {

    private static final char MASK_CHARACTER = '*';

    private final Set<String> forbiddenWords = new BadWordFiltering().stream()
            .filter(word -> !word.isBlank())
            .collect(Collectors.toUnmodifiableSet());

    // 라이브러리 change()는 단어를 차례로 치환해 겹친 금칙어가 일부만 가려지므로 원문 위치 기준으로 가린다
    public String mask(String content) {
        boolean[] masked = new boolean[content.length()];
        boolean hasForbiddenWord = false;
        for (String word : forbiddenWords) {
            for (int index = content.indexOf(word); index >= 0; index = content.indexOf(word, index + 1)) {
                Arrays.fill(masked, index, index + word.length(), true);
                hasForbiddenWord = true;
            }
        }
        if (!hasForbiddenWord) {
            return content;
        }
        StringBuilder maskedContent = new StringBuilder(content);
        for (int i = 0; i < masked.length; i++) {
            if (masked[i]) {
                maskedContent.setCharAt(i, MASK_CHARACTER);
            }
        }
        return maskedContent.toString();
    }
}
