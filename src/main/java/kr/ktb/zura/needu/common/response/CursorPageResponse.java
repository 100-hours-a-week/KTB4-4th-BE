package kr.ktb.zura.needu.common.response;

import java.util.List;

public record CursorPageResponse<T>(List<T> items, String nextCursor, boolean hasNext) {
}
