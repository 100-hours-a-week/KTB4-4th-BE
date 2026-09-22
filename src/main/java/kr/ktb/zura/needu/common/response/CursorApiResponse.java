package kr.ktb.zura.needu.common.response;

// 커서 페이지네이션 API는 명세상 nextCursor, hasNext를 data가 아닌 최상위 필드로 내려준다.
public record CursorApiResponse<T>(String message, ItemsResponse<T> data, String nextCursor, boolean hasNext) {

    public static <T> CursorApiResponse<T> of(String message, CursorPageResponse<T> page) {
        return new CursorApiResponse<>(message, new ItemsResponse<>(page.items()), page.nextCursor(), page.hasNext());
    }
}
