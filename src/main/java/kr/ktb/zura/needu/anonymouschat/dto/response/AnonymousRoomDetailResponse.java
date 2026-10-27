package kr.ktb.zura.needu.anonymouschat.dto.response;

import kr.ktb.zura.needu.anonymouschat.entity.AnonymousRoom;
import kr.ktb.zura.needu.product.dto.response.ProductSummaryResponse;

public record AnonymousRoomDetailResponse(
        Long roomId,
        boolean askedByMe,
        String targetName,
        ProductSummaryResponse product
) {

    public static AnonymousRoomDetailResponse of(
            AnonymousRoom room, boolean askedByMe, String targetName, ProductSummaryResponse product) {
        return new AnonymousRoomDetailResponse(room.getId(), askedByMe, targetName, product);
    }
}
