package kr.ktb.zura.needu.anonymouschat.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import kr.ktb.zura.needu.anonymouschat.entity.AnonymousRoom;
import kr.ktb.zura.needu.anonymouschat.repository.AnonymousRoomRepository;
import kr.ktb.zura.needu.notification.service.NotificationPreview;
import kr.ktb.zura.needu.notification.service.NotificationPreviewProvider;
import kr.ktb.zura.needu.notification.type.NotificationResourceType;
import kr.ktb.zura.needu.product.dto.response.ProductSummaryResponse;
import kr.ktb.zura.needu.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnonymousRoomNotificationPreviewProvider implements NotificationPreviewProvider {

    private final AnonymousRoomRepository anonymousRoomRepository;
    private final ProductService productService;

    @Override
    public NotificationResourceType resourceType() {
        return NotificationResourceType.ANONYMOUS_ROOM;
    }

    @Override
    public Map<Long, NotificationPreview> findAllPreviews(Long receiverUserId, Collection<Long> resourceIds) {
        if (resourceIds.isEmpty()) {
            return Map.of();
        }
        List<AnonymousRoom> rooms = anonymousRoomRepository
                .findAllByIdInAndRecipientUserId(resourceIds, receiverUserId);
        Map<Long, ProductSummaryResponse> products = productService.findAllProductSummaries(
                rooms.stream().map(AnonymousRoom::getProductId).toList());

        return rooms.stream().collect(Collectors.toMap(
                AnonymousRoom::getId,
                room -> AnonymousRoomNotificationPreview.from(products.get(room.getProductId()))));
    }
}
