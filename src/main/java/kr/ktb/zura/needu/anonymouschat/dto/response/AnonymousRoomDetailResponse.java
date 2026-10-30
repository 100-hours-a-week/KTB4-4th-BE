package kr.ktb.zura.needu.anonymouschat.dto.response;

import kr.ktb.zura.needu.anonymouschat.entity.AnonymousRoom;
import kr.ktb.zura.needu.anonymouschat.type.AnonymousRoomParticipantStatus;
import kr.ktb.zura.needu.product.dto.response.ProductSummaryResponse;

public record AnonymousRoomDetailResponse(
        Long roomId,
        boolean askedByMe,
        String targetName,
        AnonymousCounterpartProfileResponse targetProfile,
        AnonymousRoomParticipantStatus partnerStatus,
        ProductSummaryResponse product
) {

    public static AnonymousRoomDetailResponse of(
            AnonymousRoom room,
            boolean askedByMe,
            String targetName,
            AnonymousCounterpartProfileResponse targetProfile,
            ProductSummaryResponse product
    ) {
        AnonymousRoomParticipantStatus partnerStatus = askedByMe
                ? room.getRecipientStatus()
                : room.getQuestionerStatus();
        return new AnonymousRoomDetailResponse(
                room.getId(), askedByMe, targetName, targetProfile, partnerStatus, product);
    }
}
