package kr.ktb.zura.needu.friend.dto.request;

public record FriendSearchCondition(Boolean favorite, String keyword, String cursor, int size) {
}
