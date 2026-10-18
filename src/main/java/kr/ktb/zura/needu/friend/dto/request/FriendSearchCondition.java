package kr.ktb.zura.needu.friend.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record FriendSearchCondition(
        Boolean favorite,
        @Size(max = FriendSearchCondition.MAX_KEYWORD_LENGTH)
        @Pattern(regexp = FriendSearchCondition.NOT_BLANK_PATTERN)
        String keyword,
        String cursor,
        @NotNull
        @Min(FriendSearchCondition.MIN_PAGE_SIZE)
        @Max(FriendSearchCondition.MAX_PAGE_SIZE)
        Integer size
) {

    private static final int MIN_PAGE_SIZE = 1;
    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_KEYWORD_LENGTH = 20;

    // keyword는 생략할 수 있지만, 전달했다면 공백만으로 이루어질 수 없다
    private static final String NOT_BLANK_PATTERN = "(?s).*\\S.*";
}
