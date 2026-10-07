package kr.ktb.zura.needu.friend.dto.request;

public record FriendSearchCondition(String sort, Boolean favorite, String keyword, String cursor, int size) {
}
