package kr.ktb.zura.needu.product.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import kr.ktb.zura.needu.friend.service.FriendService;
import kr.ktb.zura.needu.notification.service.NotificationPreview;
import kr.ktb.zura.needu.notification.service.NotificationPreviewProvider;
import kr.ktb.zura.needu.notification.type.NotificationResourceType;
import kr.ktb.zura.needu.product.entity.ProductLinkClick;
import kr.ktb.zura.needu.product.entity.PurchaseCheck;
import kr.ktb.zura.needu.product.repository.PurchaseCheckRepository;
import kr.ktb.zura.needu.product.type.ProductContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PurchaseCheckNotificationPreviewProvider implements NotificationPreviewProvider {

    private final PurchaseCheckRepository purchaseCheckRepository;
    private final FriendService friendService;

    @Override
    public NotificationResourceType resourceType() {
        return NotificationResourceType.PURCHASE_CHECK;
    }

    @Override
    public Map<Long, NotificationPreview> findAllPreviews(Long receiverUserId, Collection<Long> resourceIds) {
        if (resourceIds.isEmpty()) {
            return Map.of();
        }
        List<PurchaseCheck> purchaseChecks =
                purchaseCheckRepository.findAllWithProductByIdInAndUserId(resourceIds, receiverUserId);
        Map<Long, String> friendNames = friendService.findAllFriendNicknames(
                receiverUserId, findFriendUserIds(purchaseChecks));

        return purchaseChecks.stream().collect(Collectors.toMap(
                PurchaseCheck::getId,
                purchaseCheck -> toPreview(purchaseCheck.getProductLinkClick(), friendNames)));
    }

    private List<Long> findFriendUserIds(List<PurchaseCheck> purchaseChecks) {
        return purchaseChecks.stream()
                .map(PurchaseCheck::getProductLinkClick)
                .filter(click -> click.getContext() == ProductContext.FRIEND_GIFT)
                .map(ProductLinkClick::getFriendUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private NotificationPreview toPreview(ProductLinkClick click, Map<Long, String> friendNames) {
        String friendName = click.getContext() == ProductContext.FRIEND_GIFT && click.getFriendUserId() != null
                ? friendNames.get(click.getFriendUserId())
                : null;
        return PurchaseCheckNotificationPreview.of(click, friendName);
    }
}
